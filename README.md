# opus-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fopus-kotlin-blue.svg)](https://github.com/KotlinMania/opus-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/opus-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/opus-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/opus-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/opus-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`SpaceManiac/opus-rs`](https://github.com/SpaceManiac/opus-rs) (crate `opus`).

**Original Project:** This port is based on [`SpaceManiac/opus-rs`](https://github.com/SpaceManiac/opus-rs). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `SpaceManiac/opus-rs`

> The text below is reproduced from [`https://github.com/SpaceManiac/opus-rs`](https://github.com/SpaceManiac/opus-rs). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs where appropriate so they continue to resolve from this repository.

# opus-rs

Safe Rust bindings for [libopus](https://opus-codec.org/). The rustdoc
includes brief descriptions for methods, and detailed API information can be
found at the [libopus documentation][upstream docs].

[crates.io] - [docs.rs] - [upstream docs]

[crates.io]: https://crates.io/crates/opus
[docs.rs]: https://docs.rs/opus/
[upstream docs]: https://opus-codec.org/docs/opus_api-1.6/

## External dependencies

By default, you need `cmake` and a C compiler.
These requirements come from [opusic-sys](https://crates.io/crates/opusic-sys).

## License

Licensed under either of

 * Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE) or http://www.apache.org/licenses/LICENSE-2.0)
 * MIT license ([LICENSE-MIT](LICENSE-MIT) or http://opensource.org/licenses/MIT)

at your option.

### Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you, as defined in the Apache-2.0 license, shall be
dual licensed as above, without any additional terms or conditions.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:opus-kotlin:0.1.0")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same license terms as the upstream [`SpaceManiac/opus-rs`](https://github.com/SpaceManiac/opus-rs). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the opus authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`SpaceManiac/opus-rs`](https://github.com/SpaceManiac/opus-rs) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
