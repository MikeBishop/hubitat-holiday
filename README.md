# Holiday Lighting for Hubitat

It's cool to use colored lights for holiday displays.  Surprisingly, there
are relatively few apps that allow scheduling different color palettes for
different holidays.

This app performs two related functions with a set of RGB lights:
- Because these lights are probably used for normal illumination, it provides
  a simple lighting-triggered-by-sensors option.
- On scheduled holidays, a holiday-themed set of colors is applied to the
  selected lights.

# Palette Scenes

Sometimes you want all the functionality of the holiday displays, but you have
your own logic for when and why. That's fine!

This app creates a series of activator devices which apply a set of colors
to a group of lights, just like Holiday Lighting does. With the activator
devices, you have full control of when this color palette starts and stops.

# Change Log

* [6/28/2022]   Initial release
* [7/5/2022]    Multiple bugs fixed; improved sunrise/sunset handling
* [7/8/2022]    More bug fixes; added lock unlocked as illumination trigger
* [7/18/2022]   Add support for Modes as an alternative to time period
* [11/30/2022]  Moved code into library; new app Palette Scenes added
* [12/12/2022]   HL: Permit setting behavior when illumination not triggered
