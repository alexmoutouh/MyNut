#include <jni.h>
#include <dlfcn.h>
#include <string>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

struct VisionEngineHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;
    const llama_vocab *vocab = nullptr;
    mtmd_context *mtmd_ctx = nullptr;
};

extern "C"
JNIEXPORT jint JNICALL
Java_com_alexm_mynut_data_llm_LlamaNative_nativePing(JNIEnv *, jobject) {
    return 42;
}

// ggml_backend_load_all() (no args) locates sibling ggml-*.so backend files by looking next to
// the process executable and in the current working directory — neither is meaningful on
// Android (the "executable" is app_process, and the process cwd is "/", which SELinux denies
// untrusted_app from listing). The upstream llama.cpp Android example (examples/llama.android)
// works around this by passing the app's native library directory explicitly to
// ggml_backend_load_all_from_path(). We don't have an Android Context available here, so we
// derive the same directory ourselves: our own shared library (libmynut_llm.so) is installed
// next to the ggml backend .so files, so dladdr() on one of our own symbols gives us the path.
static std::string find_own_library_dir() {
    Dl_info info;
    if (dladdr(reinterpret_cast<void *>(&find_own_library_dir), &info) && info.dli_fname != nullptr) {
        std::string path(info.dli_fname);
        auto last_slash = path.find_last_of('/');
        if (last_slash != std::string::npos) {
            return path.substr(0, last_slash);
        }
    }
    return "";
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_alexm_mynut_data_llm_LlamaNative_nativeLoadVisionModel(JNIEnv *env, jobject, jstring modelPath, jstring mmprojPath) {
    const std::string lib_dir = find_own_library_dir();
    if (!lib_dir.empty()) {
        ggml_backend_load_all_from_path(lib_dir.c_str());
    } else {
        ggml_backend_load_all();
    }

    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_batch = 1024;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        return 0;
    }

    const char *mmprojPathChars = env->GetStringUTFChars(mmprojPath, nullptr);
    mtmd_context_params mtmd_params = mtmd_context_params_default();
    mtmd_params.use_gpu = false;
    mtmd_context *mtmd_ctx = mtmd_init_from_file(mmprojPathChars, model, mtmd_params);
    env->ReleaseStringUTFChars(mmprojPath, mmprojPathChars);

    if (mtmd_ctx == nullptr) {
        llama_free(ctx);
        llama_model_free(model);
        return 0;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(/*penalty_last_n=*/64, /*penalty_repeat=*/1.3f, /*penalty_freq=*/0.0f, /*penalty_present=*/0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    auto *handle = new VisionEngineHandle();
    handle->model = model;
    handle->ctx = ctx;
    handle->sampler = sampler;
    handle->vocab = llama_model_get_vocab(model);
    handle->mtmd_ctx = mtmd_ctx;

    return reinterpret_cast<jlong>(handle);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alexm_mynut_data_llm_LlamaNative_nativeCompleteWithImage(
    JNIEnv *env, jobject, jlong handlePtr, jstring promptStr,
    jint imageWidth, jint imageHeight, jbyteArray imagePixels, jint maxTokens
) {
    auto *handle = reinterpret_cast<VisionEngineHandle *>(handlePtr);
    if (handle == nullptr) {
        return env->NewStringUTF("");
    }

    // The context/sampler are cached and reused across calls (see LlamaVisionEngine's
    // lazy handle). Without clearing them, a second scan's batch starts a new sequence
    // at position 0 while the KV cache still holds the first scan's positions, which
    // llama_decode rejects outright (llama-batch.cpp's position-consistency check) --
    // and the repetition-penalty sampler would otherwise carry the first scan's tokens
    // into the second. Each call must start from a clean slate.
    llama_memory_clear(llama_get_memory(handle->ctx), true);
    llama_sampler_reset(handle->sampler);

    const char *promptChars = env->GetStringUTFChars(promptStr, nullptr);
    std::string userPrompt(promptChars);
    env->ReleaseStringUTFChars(promptStr, promptChars);

    jbyte *pixelBytes = env->GetByteArrayElements(imagePixels, nullptr);
    auto *pixelData = reinterpret_cast<const unsigned char *>(pixelBytes);

    mtmd_bitmap *bitmap = mtmd_bitmap_init(
        static_cast<uint32_t>(imageWidth),
        static_cast<uint32_t>(imageHeight),
        pixelData
    );
    env->ReleaseByteArrayElements(imagePixels, pixelBytes, JNI_ABORT);

    if (bitmap == nullptr) {
        return env->NewStringUTF("");
    }

    // The main model (Qwen3-VL-*-Instruct) is instruction-tuned and expects its
    // ChatML template (<|im_start|>role ... <|im_end|>) around the user turn;
    // without it, the first sampled token is immediately end-of-generation and
    // complete() silently returns an empty string. This hand-written wrapping
    // mirrors the standard Qwen ChatML format (shared by the whole Qwen family)
    // rather than pulling in llama.cpp's full common/jinja chat-template
    // subsystem, which would require substantial additional link dependencies.
    std::string fullPrompt =
        "<|im_start|>user\n" + std::string(mtmd_default_marker()) + "\n" + userPrompt +
        "<|im_end|>\n<|im_start|>assistant\n";

    mtmd_input_text input_text;
    input_text.text = fullPrompt.c_str();
    input_text.text_len = fullPrompt.size();
    input_text.add_special = true;
    input_text.parse_special = true;

    const mtmd_bitmap *bitmaps[1] = { bitmap };
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();

    int32_t tokenize_result = mtmd_tokenize(handle->mtmd_ctx, chunks, &input_text, bitmaps, 1);
    mtmd_bitmap_free(bitmap);

    if (tokenize_result != 0) {
        mtmd_input_chunks_free(chunks);
        return env->NewStringUTF("");
    }

    llama_pos new_n_past = 0;
    int32_t eval_result = mtmd_helper_eval_chunks(
        handle->mtmd_ctx, handle->ctx, chunks,
        /*n_past=*/0, /*seq_id=*/0, /*n_batch=*/1024,
        /*logits_last=*/true, &new_n_past
    );
    mtmd_input_chunks_free(chunks);

    if (eval_result != 0) {
        return env->NewStringUTF("");
    }

    std::string result;

    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = llama_sampler_sample(handle->sampler, handle->ctx, -1);
        if (llama_vocab_is_eog(handle->vocab, new_token)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(handle->vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        llama_batch batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(handle->ctx, batch)) {
            break;
        }
    }

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_alexm_mynut_data_llm_LlamaNative_nativeUnloadVision(JNIEnv *, jobject, jlong handlePtr) {
    auto *handle = reinterpret_cast<VisionEngineHandle *>(handlePtr);
    if (handle == nullptr) {
        return;
    }

    mtmd_free(handle->mtmd_ctx);
    llama_sampler_free(handle->sampler);
    llama_free(handle->ctx);
    llama_model_free(handle->model);
    delete handle;
}
