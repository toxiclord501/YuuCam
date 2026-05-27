# YuuCam GCam Professional Subsystem

Written completely in **pure Java** for maximum compatibility and multi-thread performance on Android devices running API 21 through 34+.

## Advanced Computational Hardware Directives Implemented
1. **CameraFragment.java**:
   - Programmatically binds process life cycle providers.
   - Leverages exposure index compensation APIs (`setExposureCompensationIndex`) next to focus metering.
   - Emulates advanced timer loops, quality and speed capture switches (Min Latency vs Quality focus grids).
   - Cycles program parameters dynamically (Flash matrices, HDR state binding maps).
2. **GestureListener.java**:
   - Captures continuous scroll signals to recalculate scale variables.
   - Detects double taps to flip hardware camera selectors and spot focuses.
3. **fragment_camera.xml**: Modern, Material You centered HUD overlay layouts, processing progress markers, and EV compensation structures.

## Setup Requirements
1. Run Android Studio.
2. Select **Open** and point to this unzipped gradle repository.
3. Build the gradle modules automatically. Dependencies will resolve for `androidx.camera` CameraX extensions automatically!
