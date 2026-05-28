# QuPath Controller Extension

A [QuPath](https://github.com/qupath/qupath) extension for viewing and annotating images with a game controller.

## Installation

Requires **QuPath 0.7.0**.

1. Open QuPath and choose *Extensions > Manage extensions*
2. Click *Manage extension catalogs*
3. Click *Add* and enter this URL:
```
https://github.com/camlloyd/qupath-camlloyd-catalog
```
4. Click the `+` button next to *QuPath Controller extension*
5. Restart QuPath

> On macOS, QuPath may need accessibility and input-monitoring permissions before keyboard and mouse actions work. Grant these in *System Settings > Privacy & Security*.


## Usage

Connect your controller over USB, launch QuPath, and choose *Extensions > Controller > Controller layout...*

If QuPath is already running, click the *Refresh* button in the controller layout window.


## Compatibility

| Controller | macOS | Windows |
| :---: | :---: | :---: |
| DualSense (PS5) | ✅ | ✅ |
| Xbox 360 | ✅ | ❌ |

> USB only. Wireless is not supported or planned.


## Default layout (DualSense)

| Input | Action |
| --- | --- |
| Left joystick | Move mouse pointer |
| Right joystick | Pan viewer |
| Cross | Left click |
| Square | Right click |
| Triangle | Shift + right click |
| Circle | Close open window (hold 1 s to undo) |
| Left bumper (L1) | Zoom out |
| Right bumper (R1) | Zoom in |
| Left trigger (L2) | Previous tool |
| Right trigger (R2) | Next tool |
| Left stick click (L3) | Zoom to fit |
| Right stick click (R3) | Toggle fast / precise pan |
| D-pad up | Show/hide annotations |
| D-pad down | Fill/unfill annotations |
| D-pad left | Show/hide detections |
| D-pad right | Fill/unfill detections |
| Options | Save as |
| Create | Screenshot |
| Mute | Freeze/unfreeze controller input |
| Touch pad left | Show analysis pane |
| Touch pad bottom | Show detection measurements |
| Touch pad right | Show slide overview |
| Touch pad swipe | Pan viewer |