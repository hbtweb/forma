# Getting Started with Forma

Forma is a multi-platform UI framework that compiles EDN (Extensible Data Notation) into native UI components across different platforms.

## Prerequisites

- **Java JDK 11+** - Required for Clojure
- **Clojure CLI tools** - Install from [clojure.org](https://clojure.org/guides/getting_started)
- **Kora Core** - Local dependency (should be at `../kora/core` relative to Forma)

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/yourorg/forma.git
cd forma
```

### 2. Verify Kora Core Dependency

Ensure Kora Core is available at `../kora/core`:

```bash
ls ../kora/core
# Should show kora core files
```

### 3. Start REPL

```bash
clojure -M:repl
```

If successful, you'll see a Clojure REPL prompt.

## Your First Forma App

### Hello World (HTML)

```clojure
;; In REPL, require the compiler
(require '[forma.compiler :as compiler])

;; Compile a simple button to HTML
(compiler/compile-to-html
  [[:button {:text "Click me!"}]]
  {:platform-stack [:html]})

;; Output: "<button>Click me!</button>"
```

### Adding Styling

```clojure
;; Add design properties
(compiler/compile-to-html
  [[:section {:design/background "#1a202c"
              :design/padding "60px 20px"}
    [:heading {:text "Welcome to Forma" :level 1}]
    [:button {:text "Get Started" :variant :primary}]]]
  {:platform-stack [:html :css]})

;; Output: Styled HTML section with heading and button
```

### Multi-Platform Compilation

```clojure
;; Define your UI once
(def my-ui
  [[:button {:text "Click me" :variant :primary}]])

;; Compile to HTML
(compiler/compile-to-html my-ui {:platform-stack [:html]})

;; Compile to Oxygen (WordPress) - when you have credentials set
(compiler/compile-to-oxygen my-ui {:platform :oxygen})

;; Compile to React (when implemented)
(compiler/compile-to-react my-ui {:platform-stack [:react]})
```

## Next Steps

- **Platform Guides:** Learn platform-specific compilation
  - [HTML Guide](html-guide.md)
  - [React Guide](react-guide.md) (coming soon)
  - [Flutter Guide](flutter-guide.md) (coming soon)

- **Adding Platforms:** [Platform Extension Guide](adding-platforms.md)

- **Component Library:** Explore 56 pre-built components in `default/components/`

- **Examples:** Check `examples/` for complete projects (coming soon)

## Getting Help

- **Documentation:** See [docs/](../) for architecture and platform details
- **Issues:** Report bugs at [GitHub Issues](https://github.com/yourorg/forma/issues)
- **Community:** [Discord/Slack] (coming soon)
