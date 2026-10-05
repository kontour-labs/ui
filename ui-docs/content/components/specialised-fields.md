# `PasswordField` and the rest

*Also on this page: `SecretField`, `NumberField`, `PhoneField`, `EmailField`.*

<!--sample:SpecialisedFieldsBasics-->
```kotlin
val password = rememberTextFieldState()
val adults = rememberTextFieldState()
val phone = rememberTextFieldState()
val email = rememberTextFieldState()

// Each is `TextField` with the keyboard, the autofill hint and the
// transformation already right — the three things that get forgotten one
// at a time.
PasswordField(state = password, label = "Password", newPassword = true)
NumberField(state = adults, label = "Adults", maxLength = 2)
// Stored clean, displayed masked: the caller reads "0412345678". The
// grouping is the app's to name — the field guesses no country.
PhoneField(state = phone, label = "Mobile", format = PhoneFormat.AustralianMobile)
EmailField(state = email, label = "Email")
```
| | |
|---|---|
| `PasswordField` | Reveal toggle, autofill content type |
| `SecretField` | The same mask and toggle, kept away from autofill |
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
life. Its eye is announced as a checkbox, checked while the password shows, and
answers a press as every toggle does: a crisp tick revealing, a low one hiding.

**The character just typed shows for a moment** before it is masked, the way a
phone keyboard does — the only confirmation a reader has that the key they meant
is the key they hit. `revealLastTyped = false` turns it off, for a screen that is
likely to be watched.

**The eye says what is true now**: struck through while the password is hidden,
plain while it can be read. Pass `revealIcon` alone and the slash draws itself;
pass an already-slashed `hideIcon` and the two cross-fade.

### A key is not a password

**`SecretField` is for an API key, a token or a recovery code.** `PasswordField`
tells the platform's autofill it holds a password, which is how a password
manager knows to offer the one it saved — and how a key typed into one came to
be offered for saving as the reader's login. `SecretField` masks, reveals and
shows the character just typed exactly as `PasswordField` does, and tells the
platform none of that:

<!--sample:SecretFieldBasics-->
```kotlin
val apiKey = rememberTextFieldState()

// Masked and revealed as a password is, and not one to the platform: no
// autofill hint, so nothing offers to save it as the reader's login.
SecretField(state = apiKey, label = "API key", revealIcon = Tabler.Outline.Eye)
```

- **No autofill.** No content type, and marked as holding nothing to fill
  (`ContentDataType.None`), so Android's autofill neither offers to fill it nor,
  from the field, to save it.
- **Not a password field to the keyboard.** `KeyboardType.Password` is what
  makes iOS treat a field as secure entry and Android hint it as a password, and
  Compose adds `ContentType.Password` for it besides. `SecretField` asks for an
  ASCII keyboard with no autocorrect and no capitalisation instead.
- **In the mono face**, so a revealed key reads character by character.

What each platform still does, as far as Compose 1.12's sources show:

| | `PasswordField` | `SecretField` |
|---|---|---|
| Android | Autofill hint `password`, password input type | No hint, not autofillable; the keyboard is shown the masked text |
| iOS | Secure entry, content type password | Ordinary entry: the keyboard may learn from it and suggest it |
| Web | Neither sets `type="password"`: Compose's hidden input is always text, so a browser's own "save password" does not come from either | The same |
| Desktop | No autofill | No autofill |

The iOS row is the trade: a secure field is one the keyboard leaves alone, and
it is also one the system treats as a password. A key that should never be
learned and never be saved has no field on iOS that does both.

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
`ContentType.Password`, or `NewPassword` with `newPassword = true` so the
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

At high contrast each field answers as [`TextField`](text-field.md) does: the
border is thicker, and a `Filled` field, which has none at the standard tier,
gains an edge in the scheme's `outline`. `PasswordField`'s eye, when it has one,
is a ghost button and gains an edge in its glyph's colour, as every ghost does
at this tier.

Reduced motion changes little here, because little moves: the frame's colour and
weight and the helper line opening beneath it are short fades and tweens at
every setting. `PasswordField`'s eye draws its slash at `fast` rather than
`default`, and does not shrink when pressed.
