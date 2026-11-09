# Hercules AI Gallery

Android photo organizer (Ukrainian UI). Uses on-device ML Kit image labeling to suggest thematic albums. The app copies photos into `Pictures/Hercules AI Gallery/<category>` after user confirmation; originals are never deleted.

Build APK via GitHub Actions → Android APK → Artifacts. First version is an experimental prototype; review category assignments before copying. ML Kit model may require an initial download; labeling is not guaranteed to work offline on first launch.
