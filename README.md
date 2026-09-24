# TextMesh Tester

Small Android app for trying TextMesh Pro-style markup in two live input boxes:
- Name
- Chat

The workflow in `.github/workflows/build.yml` builds `app-debug.apk` and uploads it as a GitHub Actions artifact.

The parser covers the most useful tags from the supplied list: bold, italic, underline, strike, superscript, subscript, colors, alpha, size, alignment-related text is left available for extension, highlight/mark, case transforms, voffset, and spacing approximations.

TextMesh Pro itself is a Unity system; this Android tester approximates the visual results using Android `Spannable` rendering, so it is a preview rather than a byte-for-byte TMP renderer.
