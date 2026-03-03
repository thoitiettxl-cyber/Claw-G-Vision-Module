# DexParser API Reference

## Overview

`DexParser` cho phép phân tích DEX file trong memory để:
- Tìm tất cả callers của một method
- Scan annotations
- Phân tích obfuscated code patterns

## Getting DexParser Instance

```java
// Parse DEX từ ByteBuffer
DexParser parser = parseDex(dexData, includeAnnotations);
```

## Core Interfaces

### DexParser

```java
interface DexParser extends Closeable {
    int NO_INDEX = 0xffffffff;
    
    StringId[] getStringId();
    TypeId[] getTypeId();
    FieldId[] getFieldId();
    MethodId[] getMethodId();
    ProtoId[] getProtoId();
    Annotation[] getAnnotations();
    Array[] getArrays();
    
    void visitDefinedClasses(ClassVisitor visitor);
}
```

### ID Interfaces

```java
interface StringId extends Id<StringId> {
    String getString();
}

interface TypeId extends Id<TypeId> {
    StringId getDescriptor();  // e.g., "Lcom/example/Class;"
}

interface FieldId extends Id<FieldId> {
    TypeId getType();
    TypeId getDeclaringClass();
    StringId getName();
}

interface MethodId extends Id<MethodId> {
    TypeId getDeclaringClass();
    ProtoId getPrototype();
    StringId getName();
}

interface ProtoId extends Id<ProtoId> {
    StringId getShorty();
    TypeId getReturnType();
    TypeId[] getParameters();
}
```

## Visitor Pattern

### ClassVisitor

```java
interface ClassVisitor extends EarlyStopVisitor {
    MemberVisitor visit(
        int clazz,
        int accessFlags,
        int superClass,
        int[] interfaces,
        int sourceFile,
        int[] staticFields,
        int[] staticFieldsAccessFlags,
        int[] instanceFields,
        int[] instanceFieldsAccessFlags,
        int[] directMethods,
        int[] directMethodsAccessFlags,
        int[] virtualMethods,
        int[] virtualMethodsAccessFlags,
        int[] annotations
    );
}
```

### MethodVisitor

```java
interface MethodVisitor extends MemberVisitor {
    MethodBodyVisitor visit(
        int method,
        int accessFlags,
        boolean hasBody,
        int[] annotations,
        int[] parameterAnnotations
    );
}
```

### MethodBodyVisitor

```java
interface MethodBodyVisitor {
    void visit(
        int method,
        int accessFlags,
        int[] referredStrings,
        int[] invokedMethods,
        int[] accessedFields,
        int[] assignedFields,
        byte[] opcodes
    );
}
```

## Usage Example: Find All Callers

```java
public List<MethodId> findCallers(DexParser parser, int targetMethodIdx) {
    List<MethodId> callers = new ArrayList<>();
    MethodId[] allMethods = parser.getMethodId();
    
    parser.visitDefinedClasses(new ClassVisitor() {
        @Override
        public MemberVisitor visit(int clazz, int accessFlags, ...) {
            return new MethodVisitor() {
                @Override
                public MethodBodyVisitor visit(int method, int accessFlags, 
                        boolean hasBody, int[] annotations, int[] paramAnnotations) {
                    if (!hasBody) return null;
                    
                    return new MethodBodyVisitor() {
                        @Override
                        public void visit(int method, int accessFlags,
                                int[] referredStrings, int[] invokedMethods,
                                int[] accessedFields, int[] assignedFields,
                                byte[] opcodes) {
                            
                            for (int invoked : invokedMethods) {
                                if (invoked == targetMethodIdx) {
                                    callers.add(allMethods[method]);
                                    break;
                                }
                            }
                        }
                    };
                }
                
                @Override
                public boolean stop() { return false; }
            };
        }
        
        @Override
        public boolean stop() { return false; }
    });
    
    return callers;
}
```

## Usage Example: Scan Annotations

```java
public void scanAnnotations(DexParser parser, String annotationType) {
    Annotation[] annotations = parser.getAnnotations();
    TypeId[] types = parser.getTypeId();
    
    for (Annotation annotation : annotations) {
        TypeId type = annotation.getType();
        String descriptor = type.getDescriptor().getString();
        
        if (descriptor.equals(annotationType)) {
            Element[] elements = annotation.getElements();
            for (Element element : elements) {
                String name = element.getName().getString();
                byte[] value = element.getValue();
                // Process annotation element
            }
        }
    }
}
```

## Use Cases

| Use Case | Methods Used |
|----------|--------------|
| Find method callers | `visitDefinedClasses()`, check `invokedMethods[]` |
| Find string usages | `visitDefinedClasses()`, check `referredStrings[]` |
| Find field accessors | `visitDefinedClasses()`, check `accessedFields[]` |
| Scan annotations | `getAnnotations()`, `getType()`, `getElements()` |
| Get all methods | `getMethodId()` |
| Get all classes | `getTypeId()` |
