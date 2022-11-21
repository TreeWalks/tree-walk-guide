# Recycling Trashcan locations

Using Geospatial API provide an immersive Augmented Reality Map to guide users to the closest location. New feature: ability to show generic points of interets such as campus building, parking lots or other landmarks.

Some more details about the background: https://csaba.page/blog/geospatial-api-with-terrain-anchors-for-augmented-reality-maps.html
More development progression: https://devpost.com/software/recycling-trashcan-ar-map#updates

# Add your own locations to this application

If you wouldn't want the burden to release your own augemnted reality map app, then you can simply add your set of locations by submitting a pull request against the companion webiste. The mobile app will refresh its databse from [the website's served data](https://recyclingtrashcans.github.io/locations_v2.xml). You need to add only two changes:
1. Add a `yaml` file [to the _data folder](https://github.com/RecyclingTrashCans/RecyclingTrashCans.github.io/tree/main/_data). The format of the file is simple: title, latitude, longitude, marker type (currently it can be trahscan and poi), and an optional URL. The order doesn't matter in the yaml. You'll also want to add a unique area name (consists of alphanumeric characters and undersoce) for your locations. The optional URL is a link which will be opened if the user clicks on the marker in the app and then clicks on the Info Window again.
2. Add an extra iteration to the XML file interpolation code, right under where the `park_ridge` area is interpolated and similar way. The resulting XML data is a series of pipe separated strings, which makes the parsing of the datase easy on the mobile app and (I can avoid using heavy DOM XML parser, or even a SAX parser). Pipe separation is needed because maps URLs could contain commas, so comma separation would interfere with the parser. In the near future I'll switch to an even simpler contribution workflow, see https://github.com/RecyclingTrashCans/RecyclingTrashCans.github.io/issues/13

# Related Google Maps Platform hackathon

I also submitted the app together with the companion website to a [Google Maps Platform Hackathon](https://googlemapsplatform.devpost.com/): https://devpost.com/software/recycling-trashcan-ar-map
