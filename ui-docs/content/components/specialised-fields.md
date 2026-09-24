# `PasswordField` and the rest

*Also on this page: `NumberField`, `PhoneField`, `EmailField`.*

<!--sample:SpecialisedFieldsBasics-->
```kotlin
val password = rememberTextFieldState()
val adults = rememberTextFieldState()
val phone = rememberTextFieldState()
val email = rememberTextFieldState()

// Each is `TextField` with the keyboard, the autofill hint and the
// transformation already right — the three things that get forgotten one
// at a time.
PasswordField(state = password, label = "Password", isNewPassword = true)
NumberField(state = adults, label = "Adults", maxLength = 2)
// Stored clean, displayed masked: the caller reads "0412345678".
PhoneField(state = phone, label = "Mobile")
EmailField(state = email, label = "Email")
```
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
replaced one at a time, so every cursor offset still means what it says and a
backspace takes one character. It used to replace the whole text in one go, and a
hardware backspace on a replaced range deleted everything in it.
`KeyboardType.Password` is an IME *hint* and substitutes no glyphs; a field
relying on it alone is plaintext, which this one was for most of the project's
life.

**The character just typed shows for a moment** before it is masked, the way a
phone keyboard does — the only confirmation a reader has that the key they meant
is the key they hit. `revealLastTyped = false` turns it off, for a screen that is
likely to be watched.

**The eye says what is true now**: struck through while the password is hidden,
plain while it can be read. Pass `revealIcon` alone and the slash draws itself;
pass an already-slashed `hideIcon` and the two cross-fade.

**`NumberField` clears when its rules narrow under it.** Turn `allowDecimal` or
`allowNegative` off, or lower `maxLength`, while the field holds something the new
rules would not let in, and it empties rather than leaving a text no edit can get
out of — the filters judge the whole text an edit would leave, so `-1.5` under
digits-only rejected every edit there was, backspace included. `allowNegative`
also works without `allowDecimal` now; it used to do nothing on its own.

---

## Accessibility

Each of the four sets the platform **autofill content type**, which is the part
callers forget and the part that matters most: `PasswordField` sets
`ContentType.Password`, or `NewPassword` with `isNewPassword = true` so the
platform offers to generate and save one rather than to fill an existing one.

`PasswordField` masks with an `OutputTransformation` — one bullet per character,
so every cursor offset still means what it says.
`KeyboardType.Password` is an IME *hint* and substitutes no glyphs; a field
relying on it alone is plaintext, which this one was for most of the project's
life.

`NumberField` and `PhoneField` reject characters at the keystroke through an
`InputTransformation`, so the field never flickers through an invalid value and
no error has to be announced. The best error message is the one that never
appears.

All four take an `imeChain`, and a chain overrides their defaults —
`PasswordField` defaults to Done, which is wrong halfway down a form.
