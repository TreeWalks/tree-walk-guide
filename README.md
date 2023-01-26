# Recycling Trashcan locations

Using Geospatial API provides an augmented reality immersive guide to explore the tree walk path.
More development progression: https://devpost.com/software/tree-walk-guide#updates

# Add your own locations to this application

If you wouldn't want the burden to release your own augmented reality map app, then you can simply add your set of locations by submitting a pull request against the companion website. The mobile app will refresh its database from [the website's served data](https://recyclingtrashcans.github.io/locations_v2.xml). You need to modify only one file: [the areas.yml](https://github.com/RecyclingTrashCans/RecyclingTrashCans.github.io/tree/main/_data/areas.yml) the similar manner as the `park_ridge` area is defined.
1. Decide a unique area name (consists of alphanumeric characters and underscore) for your locations.
2. Mark it as `kind: extra`.
3. Add the series of locations: by specifying title, latitude, longitude, marker type (currently it can be `trashcan` and `poi`), and an optional URL for each. The order doesn't matter in the yaml. The optional URL is a link which will be opened if the user clicks on the marker and then clicks on the Info Window again in the app.

# Related ARCore Geospatial API hackathon

I submitted the app together with the companion website to a [ARCore GeoSpatial Hackathon](https://arcoregeospatialapi.devpost.com/): https://devpost.com/software/tree-walk-guide
