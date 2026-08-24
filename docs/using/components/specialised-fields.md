# `PasswordField` and the rest

| | |
|---|---|
| `PasswordField` | Reveal toggle, autofill content type |
| `NumberField` | Digits or decimals, rejected at the keystroke |
| `PhoneField` | Live mask, digits stored clean |
| `EmailField` | Email keyboard, autofill, no capitalisation |

Each is `TextField` with the keyboard, autofill hint and transformation already
right. They exist because those three get forgotten one at a time, and a field
that capitalises the first letter of an email address is a bug nobody files.

`PasswordField` masks with an `OutputTransformation` — one bullet per character,
so every cursor offset still means what it says. `KeyboardType.Password` is an
IME *hint* and substitutes no glyphs; a field relying on it alone is plaintext,
which this one was for most of the project's life.

---

← [Text editing](text-editing.md) · [All components](../components.md)
