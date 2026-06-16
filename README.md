# LLM Library Sample

## Introduction

This project is my attempt to abstract out different on-device large language model libraries using a common interface (with a focus on coroutines). Sample usage can be found in the `:app` module.

## Architecture

The `:llm` module is the core of the project. It's a pure Kotlin library that defines the `LLMLibrary` interface that all LLM implementations must implement.

I have also defined an optional `ModelRepository` in the `:llm` module, as an interface for fetching models. A sample implementation can be found in the `:android` module. 

I'm still on the fence if repository should have it's own module so I'm sticking it here for now.

## Usage

_Note: I eventually plan to publish this to a maven repo. In the meantime use this library by pulling it and importing to your project._

To use an existing LLM implementation in your project, pull the project and add the relevant module as a dependency. For example, to use the LiteRT-based implementation:

```kotlin
// settings.gradle.kts
includeBuild("<path to this library>") {
    dependencySubstitution {
        substitute(module("com.limdale.llm:llm")).using(project(":llm"))
        substitute(module("com.limdale.llm:android")).using(project(":android"))
        substitute(module("com.limdale.llm:litertlm-android")).using(project(":litertlm-android"))
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":android")) // need this for AndroidModelRepository
    implementation(project(":litertlm-android"))
}
```

Then instantiate an `LLMLibrary` and call `initialize` before prompting (make sure you take care of proper coroutine scoping):

```kotlin
val repository = AndroidModelRepository(context)
val llm = LiteRtLLM(repository)

llm.initialize(LLMSettings(temperature = 0.7, cacheDir = cacheDir.absolutePath))

// Observe LLM state
llm.status.collect {
    ...
}

// Send a prompt
val response = llm.prompt("Hello!")
```


# TODO
1. Add [MK Kit GenAI](https://developers.google.com/ml-kit/genai/prompt/android) - just need to get a device that can handle it
2. Add [llama.cpp](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) as well - need to learn a bit of NDK for it
3. Add a Desktop/Kotlin App sample
4. Add more LLM parameters in `LLMSettings.kt`, possibly add more Statuses as well
5. Improve Android app by adding a selector for what model(s) to download and use
6. Publish libraries
