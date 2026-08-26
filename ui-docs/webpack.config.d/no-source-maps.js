// No source map in a production bundle.
//
// The Kotlin plugin generates its webpack config with `devtool = 'source-map'`
// unconditionally, so `ui-docs.js.map` was built and — because the deploy step
// copies the distribution wholesale — shipped. Nobody fetches it without
// devtools open, but it is pure weight in the Pages artifact and it takes real
// time to emit on every CI run.
//
// Development keeps its map: that is the build you are debugging.
if (config.mode === 'production') {
    config.devtool = false;
}
