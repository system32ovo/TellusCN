# Greek island water regression fixtures

These MVT fixtures contain only the unchanged `water` layer extracted from the
Overture Maps 2026-08-19.0 base-theme tiles, fetched on 2026-09-12. The filenames
record the location and z/x/y tile coordinates. Tests use these local fixtures
without making network requests.

Source: https://overturemaps-extras-us-west-2.s3.us-west-2.amazonaws.com/tiles/2026-08-19.0/base.pmtiles

© Overture Maps Foundation / © OpenStreetMap contributors. Source data is licensed
under ODbL 1.0: https://opendatacommons.org/licenses/odbl/1-0/
Attribution: https://docs.overturemaps.org/attribution/

The Kefalonia and Zakynthos fixtures contain the Ionian Sea label polygon
(`subtype=physical`, `class=sea`, OSM relation 4497545), which covers land.
The Delos fixture contains the equivalent Aegean Sea feature (relation 4594226).
These labels must not fill holes in the actual `subtype=ocean` coastline geometry.
Mainland Greece, Mykonos, and open Ionian Sea samples provide dry/wet controls.
The distinction is documented at https://docs.overturemaps.org/schema/reference/base/water/.
