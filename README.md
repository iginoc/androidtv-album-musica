# Album - SMB Music Player for Android TV

**Album** is a lightweight, Leanback-based Android application designed for Android TV and mobile devices to stream music directly from a Samba (SMB) share. It features a tree-like folder navigation, integrated metadata retrieval, and a synchronized favorites system.

## 🚀 Features

- **Samba (SMB) Integration**: Browse and stream music directly from your NAS or PC using SMB2/3 protocols.
- **Android TV Optimized**: Fully compatible with Leanback UI, providing a seamless experience on big screens with D-pad navigation.
- **Tree Navigation**: Intuitive folder structure navigation (Expand/Collapse) to manage large music libraries.
- **Cloud-Synced Favorites**: Save your favorite tracks to a `.m3u` playlist stored directly on your Samba share, ensuring your favorites are synchronized across all your devices (TV, Smartphone, etc.).
- **Metadata Support**: Automatically retrieves ID3 tags (Title, Artist, Album Art) using `MediaMetadataRetriever` and `mp3agic`.
- **Advanced Playback**: Powered by **androidx.media3 (ExoPlayer)** with optimized buffering for near-instant playback.
- **Mini-Player & Global Player**: Keep the music playing while you continue to browse your library.

## 🛠 Tech Stack

- **Kotlin**: 100% primary language.
- **Leanback Support Library**: For the native Android TV experience.
- **androidx.media3 (ExoPlayer)**: High-performance audio streaming.
- **jcifs-ng**: Next-generation SMB client for Java/Android.
- **Glide**: Efficient image loading for album covers.

## ⚙️ Configuration

Upon first launch, the app will request your Samba configuration:
- **SMB URL**: e.g., `smb://192.168.1.100/Music/`
- **Username**: Your Samba username (optional).
- **Password**: Your Samba password (optional).

## 📥 Installation

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and deploy the `:app` module to your Android TV or Emulator.

## 🤝 Contributing

Feel free to open issues or submit pull requests to improve the app!

---
*Developed with ❤️ for music lovers who host their own libraries.*
