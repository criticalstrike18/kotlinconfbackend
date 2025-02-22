package org.jetbrains.kotlinApp.ui

import androidx.compose.ui.graphics.drawscope.DrawScope

//@OptIn(ExperimentalResourceApi::class)
//enum class Floor(
//    override val title: StringResource,
//    val resourceLight: String,
//    val resourceDark: String,
//) : Tab {
//    GROUND(
//        Res.string.floor_1,
//        "files/ground-floor.svg",
//        "files/ground-floor-dark.svg"
//    ),
//    FIRST(
//        Res.string.floor_2,
//        "files/first-floor.svg",
//        "files/first-floor-dark.svg",
//    );
//}
//
//val Floor.resource: String
//    @Composable get() = if (isSystemInDarkTheme()) resourceDark else resourceLight
//
//@OptIn(ExperimentalResourceApi::class)
//@Composable
//fun LocationScreen() {
//    var floor: Floor by remember { mutableStateOf(Floor.GROUND) }
//    var svg: Svg? by remember { mutableStateOf(null) }
//    val path = floor.resource
//    val state = rememberZoomableState()
//    val isScreenTooWide = Screen.isTooWide()
//
//    LaunchedEffect(path) {
//        svg = Svg(Res.readBytes(path))
//        state.contentScale = FixedScale(if (isScreenTooWide) 1.7f else 2.7f)
//    }
//
//    Box(
//        Modifier
//            .fillMaxSize()
//            .background(MaterialTheme.colors.mapColor)
//    ) {
//        Canvas(
//            Modifier
//                .fillMaxSize()
//                .zoomable(state)
//        ) {
//            val currentSvg = svg ?: return@Canvas
//            val scale = size.width / currentSvg.width
//            val imageHeight = currentSvg.height * scale
//            val offsetY = (size.height - imageHeight) / 2
//
//            translate(0f, offsetY) {
//                scale(scale, pivot = Offset.Zero) {
//                    currentSvg.renderTo(this)
//                }
//            }
//        }
//        TabBar(
//            Floor.entries,
//            selected = floor,
//            onSelect = { floor = it },
//        )
//    }
//}
//
expect class Svg(svgBytes: ByteArray) {
    val width: Float
    val height: Float

    fun renderTo(scope: DrawScope)
}