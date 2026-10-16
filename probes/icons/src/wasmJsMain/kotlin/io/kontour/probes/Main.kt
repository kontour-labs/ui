package io.kontour.probes

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.AlertTriangle
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Bell
import io.kontour.icons.tabler.outline.Bookmark
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.Calendar
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.Checks
import io.kontour.icons.tabler.outline.ChevronDown
import io.kontour.icons.tabler.outline.ChevronRight
import io.kontour.icons.tabler.outline.Copy
import io.kontour.icons.tabler.outline.CurrentLocation
import io.kontour.icons.tabler.outline.Dots
import io.kontour.icons.tabler.outline.Eye
import io.kontour.icons.tabler.outline.Filter
import io.kontour.icons.tabler.outline.Flag2
import io.kontour.icons.tabler.outline.GripVertical
import io.kontour.icons.tabler.outline.HelpCircle
import io.kontour.icons.tabler.outline.Home
import io.kontour.icons.tabler.outline.InfoCircle
import io.kontour.icons.tabler.outline.Layers
import io.kontour.icons.tabler.outline.Map
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.MessageCircle
import io.kontour.icons.tabler.outline.Messages
import io.kontour.icons.tabler.outline.Minus
import io.kontour.icons.tabler.outline.Moon
import io.kontour.icons.tabler.outline.Navigation
import io.kontour.icons.tabler.outline.Note
import io.kontour.icons.tabler.outline.Notes
import io.kontour.icons.tabler.outline.Paperclip
import io.kontour.icons.tabler.outline.Pin
import io.kontour.icons.tabler.outline.Plane
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Route
import io.kontour.icons.tabler.outline.Search
import io.kontour.icons.tabler.outline.Settings
import io.kontour.icons.tabler.outline.Share
import io.kontour.icons.tabler.outline.Sparkles
import io.kontour.icons.tabler.outline.Stack
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.Ticket
import io.kontour.icons.tabler.outline.Train
import io.kontour.icons.tabler.outline.Trash
import io.kontour.icons.tabler.outline.User
import io.kontour.icons.tabler.outline.Volume
import io.kontour.icons.tabler.outline.Walk
import io.kontour.icons.tabler.outline.Wifi
import io.kontour.icons.tabler.outline.Windmill
import io.kontour.icons.tabler.outline.X
import io.kontour.ui.foundation.Icon
import io.kontour.ui.theme.KontourTheme

// The fifty the gallery draws most, so that they are a fair sample of an
// app's: arrows and pins as well as a star.
private val icons = listOf(
    Tabler.Outline.Star,
    Tabler.Outline.Plus,
    Tabler.Outline.Bus,
    Tabler.Outline.Check,
    Tabler.Outline.ChevronDown,
    Tabler.Outline.Train,
    Tabler.Outline.Minus,
    Tabler.Outline.Route,
    Tabler.Outline.InfoCircle,
    Tabler.Outline.Trash,
    Tabler.Outline.Bell,
    Tabler.Outline.Share,
    Tabler.Outline.Search,
    Tabler.Outline.Map,
    Tabler.Outline.Home,
    Tabler.Outline.X,
    Tabler.Outline.User,
    Tabler.Outline.MapPin,
    Tabler.Outline.CurrentLocation,
    Tabler.Outline.Archive,
    Tabler.Outline.AlertTriangle,
    Tabler.Outline.Stack,
    Tabler.Outline.Pin,
    Tabler.Outline.Dots,
    Tabler.Outline.Copy,
    Tabler.Outline.Notes,
    Tabler.Outline.GripVertical,
    Tabler.Outline.Filter,
    Tabler.Outline.ChevronRight,
    Tabler.Outline.Bookmark,
    Tabler.Outline.Walk,
    Tabler.Outline.Ticket,
    Tabler.Outline.Plane,
    Tabler.Outline.Paperclip,
    Tabler.Outline.Moon,
    Tabler.Outline.Messages,
    Tabler.Outline.MessageCircle,
    Tabler.Outline.Flag2,
    Tabler.Outline.Checks,
    Tabler.Outline.Sparkles,
    Tabler.Outline.Settings,
    Tabler.Outline.Note,
    Tabler.Outline.Navigation,
    Tabler.Outline.Layers,
    Tabler.Outline.HelpCircle,
    Tabler.Outline.Eye,
    Tabler.Outline.Calendar,
    Tabler.Outline.Windmill,
    Tabler.Outline.Wifi,
    Tabler.Outline.Volume,
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KontourTheme { Row { icons.forEach { Icon(it, contentDescription = null) } } } }
}
