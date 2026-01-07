# Platform Comparison Matrix

This document compares Forma's platform targets, their status, capabilities, and use cases.

## Platform Status Overview

| Platform | Status | Maturity | Compiler | Output | Best For |
|----------|--------|----------|----------|--------|----------|
| **HTML/CSS/HTMX** | ✅ Production | 100% | Complete | HTML strings | Web apps, SSR |
| **Oxygen Builder** | ✅ Production | 100% | Complete | JSON tree | WordPress sites |
| **React/JSX** | 🔧 Experimental | 40% | In Progress | JSX code | React SPAs |
| **Flutter** | 🔬 Planned | 0% | Not Started | Dart code | Mobile apps (iOS/Android) |
| **SwiftUI** | 🔬 Planned | 0% | Not Started | Swift code | Native iOS/macOS |
| **Three.js/WebGL** | 🔬 Planned | 0% | Not Started | JavaScript | 3D graphics, games |
| **Unity** | 🔬 Planned | 0% | Not Started | C# code | 3D games, VR/AR |
| **Unreal** | 🔬 Planned | 0% | Not Started | Blueprint/C++ | AAA games, simulations |
| **Godot** | 🔬 Planned | 0% | Not Started | GDScript | Indie games, 2D/3D |

## Feature Comparison

### Web Platforms

| Feature | HTML/HTMX | React | Notes |
|---------|-----------|-------|-------|
| **Semantic HTML** | ✅ Full | 🟡 Partial | HTML uses native elements, React uses divs |
| **CSS Styling** | ✅ Full | ✅ Full | Both support CSS-in-JS and external CSS |
| **HTMX Attributes** | ✅ Full | ❌ N/A | HTMX-specific feature |
| **React Hooks** | ❌ N/A | ✅ Full | React-specific state management |
| **SSR Support** | ✅ Full | ✅ Full | Both support server-side rendering |
| **Client Interactivity** | 🟡 HTMX | ✅ Full | HTMX for partial updates, React for full SPA |
| **Bundle Size** | ✅ Minimal | 🟡 Moderate | HTML = no runtime, React = framework overhead |
| **SEO** | ✅ Excellent | 🟡 Good | HTML is SEO-native, React requires SSR |

### Mobile Platforms

| Feature | Flutter | SwiftUI | Notes |
|---------|---------|---------|-------|
| **Cross-Platform** | ✅ iOS/Android/Web/Desktop | ❌ Apple Only | Flutter is truly cross-platform |
| **Native Performance** | ✅ Compiled | ✅ Native | Both compile to native code |
| **Platform Widgets** | 🟡 Material/Cupertino | ✅ Native | Flutter emulates, SwiftUI uses system |
| **Hot Reload** | ✅ Full | ✅ Full | Both support live updates |
| **Animation** | ✅ Full | ✅ Full | Both have robust animation systems |
| **State Management** | ✅ Multiple options | ✅ Built-in | Flutter: Provider/Riverpod, SwiftUI: @State/@Binding |

### Game Engine Platforms

| Feature | Unity | Unreal | Godot | Notes |
|---------|-------|--------|-------|-------|
| **2D Support** | ✅ Full | 🟡 Basic | ✅ Full | Godot excels at 2D |
| **3D Support** | ✅ Full | ✅ AAA-grade | ✅ Full | Unreal for photorealism |
| **VR/AR** | ✅ Full | ✅ Full | 🟡 Basic | Unity/Unreal industry standard |
| **Scripting** | C# | C++/Blueprint | GDScript | Different language ecosystems |
| **Asset Store** | ✅ Huge | ✅ Large | 🟡 Growing | Unity has largest marketplace |
| **Open Source** | ❌ No | 🟡 Source-available | ✅ Yes | Godot fully open source |

## Element Support Matrix

### Layout Elements

| Element | HTML | React | Flutter | SwiftUI | Unity |
|---------|------|-------|---------|---------|-------|
| `:section` | `<section>` | `<section>` | `Container` | `VStack/HStack` | `GameObject` |
| `:div` | `<div>` | `<div>` | `Container` | `VStack` | `GameObject` |
| `:header` | `<header>` | `<header>` | `AppBar` | `NavigationView` | `Canvas` |
| `:footer` | `<footer>` | `<footer>` | `BottomAppBar` | `TabView` | `Canvas` |

### Interactive Elements

| Element | HTML | React | Flutter | SwiftUI | Unity |
|---------|------|-------|---------|---------|-------|
| `:button` | `<button>` | `<Button>` | `ElevatedButton` | `Button` | `UI.Button` |
| `:text-input` | `<input>` | `<input>` | `TextField` | `TextField` | `InputField` |
| `:checkbox` | `<input type="checkbox">` | `<input type="checkbox">` | `Checkbox` | `Toggle` | `Toggle` |
| `:dropdown` | `<select>` | `<select>` | `DropdownButton` | `Picker` | `Dropdown` |

### Content Elements

| Element | HTML | React | Flutter | SwiftUI | Unity |
|---------|------|-------|---------|---------|-------|
| `:heading` | `<h1>`-`<h6>` | `<h1>`-`<h6>` | `Text` (styled) | `Text` (styled) | `TextMeshPro` |
| `:text` | `<p>` | `<p>` | `Text` | `Text` | `TextMeshPro` |
| `:image` | `<img>` | `<img>` | `Image` | `Image` | `Image` |
| `:link` | `<a>` | `<a>` | `InkWell` | `Link` | `N/A` |

## Styling Capabilities

### CSS Properties

| Property | HTML/CSS | React | Flutter | SwiftUI | Notes |
|----------|----------|-------|---------|---------|-------|
| **Colors** | ✅ Full | ✅ Full | ✅ Full | ✅ Full | All platforms |
| **Spacing (padding/margin)** | ✅ Full | ✅ Full | ✅ Full | ✅ Full | All platforms |
| **Flexbox** | ✅ Full | ✅ Full | ✅ Flex | ✅ Stacks | Similar concepts |
| **Grid** | ✅ Full | ✅ Full | ✅ GridView | ✅ LazyVGrid | Platform-specific |
| **Shadows** | ✅ Full | ✅ Full | ✅ BoxShadow | ✅ Shadow | All platforms |
| **Gradients** | ✅ Full | ✅ Full | ✅ Gradient | ✅ Gradient | All platforms |
| **Animations** | ✅ CSS/JS | ✅ React Spring | ✅ Built-in | ✅ Built-in | Platform-specific APIs |

### Design Tokens

| Token Type | All Platforms | Notes |
|------------|---------------|-------|
| **Colors** | ✅ Supported | Transformed to platform format |
| **Spacing** | ✅ Supported | px, rem, dp, points, etc. |
| **Typography** | ✅ Supported | Font families, sizes, weights |
| **Border Radius** | ✅ Supported | Platform-specific units |
| **Breakpoints** | 🟡 Web Only | Mobile uses device size |

## Use Case Recommendations

### Web Applications

**Best Choice:** HTML/HTMX or React
- **HTML/HTMX:** Server-rendered apps, content sites, simple interactivity
- **React:** SPAs, complex client-side state, real-time updates

**Example:**
```clojure
;; Blog/CMS → HTML/HTMX
;; Dashboard/Admin → React
;; E-commerce → HTML/HTMX (for SEO) + React (for cart/checkout)
```

### Mobile Applications

**Best Choice:** Flutter (cross-platform) or SwiftUI (iOS-only)
- **Flutter:** Target iOS + Android with single codebase
- **SwiftUI:** iOS/macOS native apps with best performance

**Example:**
```clojure
;; Consumer app (iOS + Android) → Flutter
;; Apple ecosystem app (iOS/macOS/watchOS) → SwiftUI
```

### WordPress Sites

**Best Choice:** Oxygen Builder
- Compiles directly to WordPress page builder
- Visual editing in Oxygen UI
- No deployment complexity

**Example:**
```clojure
;; Marketing sites → Oxygen Builder
;; Corporate websites → Oxygen Builder
;; WordPress themes → Oxygen Builder
```

### Games

**Best Choice:** Depends on project
- **Unity:** General-purpose, mobile, 2D/3D
- **Unreal:** AAA graphics, VR/AR, photorealism
- **Godot:** Indie, 2D-focused, open source

**Example:**
```clojure
;; Mobile game → Unity
;; VR experience → Unreal
;; Pixel art game → Godot
```

## Performance Characteristics

### Build Time

| Platform | Build Time | Incremental | Notes |
|----------|------------|-------------|-------|
| **HTML** | ✅ Instant | ✅ Fast | No compilation |
| **React** | 🟡 Moderate | ✅ Fast | Webpack/Vite bundling |
| **Oxygen** | ✅ Fast | ✅ Fast | JSON upload |
| **Flutter** | 🔴 Slow | 🟡 Moderate | Dart compilation |
| **SwiftUI** | 🔴 Slow | 🟡 Moderate | Swift compilation |

### Runtime Performance

| Platform | Performance | Notes |
|----------|-------------|-------|
| **HTML** | ✅ Excellent | Native browser rendering |
| **React** | 🟡 Good | Virtual DOM overhead |
| **Oxygen** | ✅ Excellent | Native WordPress/PHP |
| **Flutter** | ✅ Excellent | Compiled to native |
| **SwiftUI** | ✅ Excellent | Native iOS framework |

### Bundle Size

| Platform | Size | Notes |
|----------|------|-------|
| **HTML** | ✅ Minimal | No runtime |
| **React** | 🟡 Moderate | ~40KB React + ReactDOM |
| **Oxygen** | ✅ Minimal | Server-side |
| **Flutter** | 🔴 Large | ~4MB base app |
| **SwiftUI** | ✅ Small | System framework |

## Migration Paths

### From HTML to React

```clojure
;; Same Forma code, different platform
(def my-ui [[:button {:text "Click"}]])

;; HTML
(compiler/compile-to-html my-ui {:platform-stack [:html]})

;; React
(compiler/compile-to-react my-ui {:platform-stack [:react]})
```

**Considerations:**
- Event handlers change (`:onClick` → `onClick={...}`)
- State management needs React hooks
- Styling: CSS classes → CSS-in-JS or styled-components

### From React to React Native

```clojure
;; React Native platform (future)
(compiler/compile-to-react-native my-ui {:platform-stack [:react-native]})
```

**Considerations:**
- `<div>` → `<View>`
- `<span>` → `<Text>`
- No CSS, use inline styles or styled-components
- Platform-specific APIs (camera, geolocation, etc.)

### From Flutter to SwiftUI

**Not recommended** - Better to compile fresh from Forma:

```clojure
;; Compile same Forma code to both platforms
(compiler/compile-to-flutter my-ui {:platform-stack [:flutter]})
(compiler/compile-to-swiftui my-ui {:platform-stack [:swiftui]})
```

## Platform Selection Decision Tree

```
Need a UI?
├─ Web?
│  ├─ SEO critical? → HTML/HTMX
│  ├─ Complex SPA? → React
│  └─ WordPress? → Oxygen Builder
│
├─ Mobile?
│  ├─ iOS + Android? → Flutter
│  ├─ iOS only? → SwiftUI
│  └─ Native performance critical? → SwiftUI/Flutter
│
├─ Desktop?
│  ├─ Cross-platform? → Flutter
│  ├─ macOS only? → SwiftUI
│  └─ Windows/Linux? → Flutter or HTML (Electron)
│
└─ Game/3D?
   ├─ AAA graphics? → Unreal
   ├─ Mobile game? → Unity
   ├─ Indie/2D? → Godot
   └─ Web 3D? → Three.js
```

## Conclusion

Forma's multi-platform approach allows you to:
1. **Write UI once** in platform-agnostic EDN
2. **Compile to native** platform code
3. **Switch platforms** with minimal effort
4. **Mix and match** platforms for different parts of your app

Choose the platform that best fits your:
- Target audience (web, mobile, desktop, game)
- Performance requirements
- Development timeline
- Team expertise
- Distribution channels

The beauty of Forma is that you can **change your mind later** - your UI code remains the same, just change the compilation target.
