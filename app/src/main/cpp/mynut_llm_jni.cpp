#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <vector>
#include "llama.h"

struct EngineHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;
    const llama_vocab *vocab = nullptr;
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
Java_com_alexm_mynut_data_llm_LlamaNative_nativeLoadModel(JNIEnv *env, jobject, jstring modelPath) {
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
    ctx_params.n_batch = 512;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        return 0;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sampler_params);
    // Pure greedy decoding with no repetition penalty is prone to degenerate repetition
    // loops on smaller models (observed in practice: real completions collapsing into the
    // same short phrase repeated hundreds of times instead of stopping at EOS). Penalize
    // recently-used tokens over the last 64 generated tokens before picking the argmax —
    // this keeps decoding effectively deterministic while breaking those loops.
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(/*penalty_last_n=*/64, /*penalty_repeat=*/1.3f, /*penalty_freq=*/0.0f, /*penalty_present=*/0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    auto *handle = new EngineHandle();
    handle->model = model;
    handle->ctx = ctx;
    handle->sampler = sampler;
    handle->vocab = llama_model_get_vocab(model);

    return reinterpret_cast<jlong>(handle);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alexm_mynut_data_llm_LlamaNative_nativeComplete(JNIEnv *env, jobject, jlong handlePtr, jstring promptStr, jint maxTokens) {
    auto *handle = reinterpret_cast<EngineHandle *>(handlePtr);
    if (handle == nullptr) {
        return env->NewStringUTF("");
    }

    const char *promptChars = env->GetStringUTFChars(promptStr, nullptr);
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(promptStr, promptChars);

    const int n_prompt = -llama_tokenize(handle->vocab, prompt.c_str(), (int) prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt);
    llama_tokenize(handle->vocab, prompt.c_str(), (int) prompt.size(), prompt_tokens.data(), (int) prompt_tokens.size(), true, true);

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), (int) prompt_tokens.size());

    std::string result;
    int n_pos = 0;

    while (n_pos + batch.n_tokens < n_prompt + maxTokens) {
        if (llama_decode(handle->ctx, batch)) {
            break;
        }
        n_pos += batch.n_tokens;

        llama_token new_token = llama_sampler_sample(handle->sampler, handle->ctx, -1);
        if (llama_vocab_is_eog(handle->vocab, new_token)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(handle->vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        batch = llama_batch_get_one(&new_token, 1);
    }

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_alexm_mynut_data_llm_LlamaNative_nativeUnload(JNIEnv *, jobject, jlong handlePtr) {
    auto *handle = reinterpret_cast<EngineHandle *>(handlePtr);
    if (handle == nullptr) {
        return;
    }

    llama_sampler_free(handle->sampler);
    llama_free(handle->ctx);
    llama_model_free(handle->model);
    delete handle;
}
