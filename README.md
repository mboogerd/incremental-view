# Incremental View

This is a small laboratory project exploring the possibilities of making
views incremental. i.e. for any change `d` in some source dataset `X`
for a view `Y`, we require some algorithm to maintain a consistency
relation `R` between `X` and `Y`, which uses only `d` and `Y` to
compute a new `Y`