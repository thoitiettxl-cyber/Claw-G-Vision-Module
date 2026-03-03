# LibXposed Architecture

## Module Loading Flow

```mermaid
sequenceDiagram
    participant Zygote
    participant Framework as Xposed Framework
    participant Process as App Process
    participant Module as XposedModule
    
    Zygote->>Process: fork() new app process
    Framework->>Framework: Detect module APK
    Framework->>Process: Inject module classes
    Framework->>Module: new XposedModule(base, param)
    Note over Module: Constructor được gọi
    Process->>Process: Load first package
    Framework->>Module: onPackageLoaded(param)
    Note over Module: Register hooks here
    Process->>Process: Load additional packages
    Framework->>Module: onPackageLoaded(param) for each
```

## Hook Execution Flow

```mermaid
sequenceDiagram
    participant Caller
    participant Framework
    participant Hooker
    participant Original as Original Method
    
    Caller->>Framework: Call hooked method
    Framework->>Hooker: before(BeforeHookCallback)
    
    alt returnAndSkip() called
        Hooker-->>Framework: Skip original
    else Continue
        Framework->>Original: Execute original method
        Original-->>Framework: Return result
    end
    
    Framework->>Hooker: after(AfterHookCallback, context?)
    Hooker-->>Framework: Optional: setResult() / setThrowable()
    Framework-->>Caller: Final result
```

## IPC Architecture

```mermaid
graph TB
    subgraph "Module App Process"
        A[XposedServiceHelper] --> B[OnServiceListener]
        C[XposedProvider] --> A
    end
    
    subgraph "Framework Process"
        D[IXposedService] --> E[Scope Manager]
        D --> F[Preferences Storage]
        D --> G[File Storage]
    end
    
    subgraph "Hooked App Process"
        H[XposedModule] --> I[XposedInterface]
        I --> J[Remote Preferences]
        I --> K[Remote Files]
    end
    
    C -.->|ContentProvider.call| D
    D -.->|Binder| C
    H -.->|Injected| D
```

## IPC Connection Flow

```mermaid
sequenceDiagram
    participant App as Module App
    participant Provider as XposedProvider
    participant Framework as Framework Process
    participant Storage as Remote Storage
    
    Note over App,Framework: Connection Phase
    Framework->>Provider: ContentProvider.call("SendBinder", binder)
    Provider->>App: OnServiceListener.onServiceBind()
    
    Note over App,Framework: Operation Phase
    App->>Framework: IXposedService.getRemotePreferences("group")
    Framework->>Storage: Read/Create preferences
    Storage-->>Framework: Preferences data
    Framework-->>App: Bundle with preferences
    
    App->>Framework: IXposedService.updateRemotePreferences(diff)
    Framework->>Storage: Write preferences
```

## Class Hierarchy

```mermaid
classDiagram
    XposedInterface <|-- XposedInterfaceWrapper
    XposedInterfaceWrapper <|-- XposedModule
    XposedModuleInterface <|.. XposedModule
    
    class XposedInterface {
        <<interface>>
        +hook(Method, Hooker)
        +hook(Constructor, Hooker)
        +deoptimize(Method)
        +invokeOrigin(...)
        +invokeSpecial(...)
        +getRemotePreferences(String)
        +log(String)
    }
    
    class XposedModule {
        <<abstract>>
        +XposedModule(XposedInterface, ModuleLoadedParam)
        +onPackageLoaded(PackageLoadedParam)
        +onSystemServerLoaded(SystemServerLoadedParam)
    }
    
    class XposedModuleInterface {
        <<interface>>
        +onPackageLoaded(PackageLoadedParam)
        +onSystemServerLoaded(SystemServerLoadedParam)
    }
```

## Privilege Levels

| Level | Value | Description | IPC Support |
|-------|-------|-------------|-------------|
| ROOT | 0 | Framework chạy với root | ✅ Full |
| CONTAINER | 1 | Container với fake system_server | ✅ Full |
| APP | 2 | Framework chạy như app | ✅ Limited |
| EMBEDDED | 3 | Framework embedded trong app | ❌ None |

## Data Flow

### Write Preferences (Module App)

```
Module App → XposedService.getRemotePreferences()
           → SharedPreferences.Editor.putXxx()
           → SharedPreferences.Editor.apply()
           → IXposedService.updateRemotePreferences()
           → Framework Storage
```

### Read Preferences (Hooked Process)

```
Hooked Process → XposedInterface.getRemotePreferences()
               → Framework Storage (via Binder)
               → SharedPreferences (read-only)
```

## AndroidManifest Requirements

```xml
<!-- XposedProvider for IPC -->
<provider
    android:name="io.github.libxposed.service.XposedProvider"
    android:authorities="${applicationId}.XposedService"
    android:exported="true" />

<!-- Module metadata -->
<meta-data
    android:name="xposedmodule"
    android:value="true" />
<meta-data
    android:name="xposedminversion"
    android:value="100" />
```

## xposed_init File

Khai báo entry class trong `assets/xposed_init`:

```
com.example.module.MainHook
```
