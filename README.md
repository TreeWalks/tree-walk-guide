# Tree Walk Guide

Using Geospatial API provides an augmented reality immersive guide to explore the tree walk path.
More development progression: https://devpost.com/software/tree-walk-guide#updates

# Spin out your own version of to this application

1. The walk metadata is stored in multiple files: a localization independent data set https://github.com/TreeWalks/tree-walk-guide/blob/main/app/src/main/res/values/locations.xml and localization dependent data set in each supported locales, in the exact same order as the `locations.xml`, right now [location_en.xml](https://github.com/TreeWalks/tree-walk-guide/blob/main/app/src/main/res/values/locations_en.xml) and [locations_es.xml](https://github.com/TreeWalks/tree-walk-guide/blob/main/app/src/main/res/values/locations_es.xml).
2. The mobile app has its own localized resources related to mobile specific UX, that follows the standard Android localization practices: default English language strings are extracted to [values/strings.xml](https://github.com/TreeWalks/tree-walk-guide/blob/main/app/src/main/res/values/strings.xml) and the corresponding Spanish ones in [values-es/strings.xml](https://github.com/TreeWalks/tree-walk-guide/blob/main/app/src/main/res/values-es/strings.xml).
3. You possibly first want to [modify the companion website](https://github.com/TreeWalks/TreeWalks.github.io) because the mobile app updates its dataset (the XML files) from fresh ones fromt he website. If you decide to go without a companion website then you can disable that mechanism by short circuiting [TreeWalkGeoActivity.downloadAllDataAsync](https://github.com/TreeWalks/tree-walk-guide/blob/17b3f373b5f0eb5b681a81459ee301492a37ffc3/app/src/main/java/dev/csaba/armap/treewalk/TreeWalkGeoActivity.kt#L533).
4. Any metadata change you employ needs to be done in concert with the companion website.

# Related ARCore Geospatial API hackathon

I submitted the app together with the companion website to a [ARCore GeoSpatial Hackathon](https://arcoregeospatialapi.devpost.com/): https://devpost.com/software/tree-walk-guide
