# DSL

<!--toc:start-->
- [DSL](#dsl)
  - [Goals](#goals)
    - [Builtin](#builtin)
    - [Functions](#functions)
    - [Literals](#literals)
  - [Non Goals](#non-goals)
  - [Language Design](#language-design)
    - [Builtin Operations](#builtin-operations)
<!--toc:end-->

## Goals

### Builtin

The DSL's grammar should stay very simple, preferably a lisp like language.
The same goes for functionality. Some operations, like binary operators,
should be implemented as builtin functions. All extended functionality must
be implemented by the developer of the script.

### Functions

Functions are polymorphic by default. The grammar must be defined in a way to
support both type annotations (with possibly subtyping constraints).
This must adhere to the currently implemented type systems:

- `Algorithm W`
- `System F`
- `HM(X)`

Though the DSL is lispy like, functions _should not support closures_.
This is not directly supported by the IR,
and a non-goal is to add a large amount of code transformation.
Additionally, closures require some form of memory management,
albeit manual memory management would also suffice.
But even manual memory management isn't currently supported!
One may argue, that the IR Values are actually just java objects,
and are garbage collected, hence allowing for closures.
But this is specifically not a design choice.
The Memory Operations support specifically only garbage collected Arrays.
There exists no explicit other memory management strategy
outside of the memory ops.

### Literals

Generally speaking, on the following literals are directly supported:

- `Numbers` (all numerics that the IR supports)
- `Strings`
- `Arrays` (Only basic array as the memory ops implement)
- `Tuples` (Those need an additional Operation Definition)
- `Unit` (the empty type, aka. `void`)

## Non Goals

- `Closures`
- All Operations that _require extension_ of the already _implemented dialects_
  (primarily `Structs`/`Records`, except `Tuples`)
- Full Type-System agnosticity analog to the IR type systems
  - While the DSL is designed to work with `HM(X)`, `Algowithm W` and `System F`,
      there are _not guarantees_ that this langauge will ever _work with any other
      type system_

## Language Design

### Builtin Operations

| Builtin                 | Description                                                                                                                                                                   |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `defn`                  | define a simple function. This function takes a fixed amount of parameters (contrary to Clojure and other lists)                                                              |
| binary operators        | Like +, - , *, /                                                                                                                                                              |
| unary operators         | Like ! or ~ (negation)                                                                                                                                                        |
| normal funciton calling | some functions will get inferred based on the operations defined in the ir. For thos,e, no clojure function needs and even can be defined. Examples are all string operations |

```clojure

(defn some-function [arg1 arg2]
  (+ arg1 arg2))

(defn some-other-function [arg1: int32 arg2: float32]
  (- arg1 arg2))

```
