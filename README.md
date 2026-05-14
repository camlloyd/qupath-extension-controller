# QuPath Controller Extension

**A [QuPath](https://github.com/qupath/qupath) extension for viewing and annotating images with a game controller.**

Currently supports the DualSense (PS5) controller, tested on macOS and Windows 11 over USB.

## Installation

Requires **QuPath 0.7.0**.

Download the latest jar from the [Releases](../../releases/latest) page and drag it onto the QuPath main window.

You can also find the extensions folder via *Extensions > Manage extensions*.

> On macOS, QuPath may need accessibility and input-monitoring permissions before keyboard and mouse actions work. Grant these in *System Settings > Privacy & Security*.

---

## Default layout

Connect your controller over USB and open *Extensions > Controller > Controller layout...*
 
If you connect after QuPath is already running, use the *Refresh* button in the layout window.

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