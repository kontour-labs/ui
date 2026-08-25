# `Accordion`

![Accordion, collapsed](../../../ui-catalog/screenshots/components/accordion-light.png)
![Accordion, expanded](../../../ui-catalog/screenshots/components/accordion-expanded-light.png)

Disclosure with hoisted state, so the caller decides what is open — including
opening the section containing whatever the user searched for.

<!--sample:AccordionBasics-->
```kotlin
Accordion(
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    header = { +"Accessibility" },
) {
    Text("Step-free access at all platforms.")
}
```

---

← [Display and content](display.md) · [All components](../components.md)
