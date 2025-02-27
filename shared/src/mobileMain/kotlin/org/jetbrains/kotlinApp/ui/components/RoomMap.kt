package org.jetbrains.kotlinApp.ui.components

//enum class Room(
//    val title: String,
//    val floor: Floor,
//    val x: Float,
//    val y: Float,
//    val zoom: Float
//) {
//    ROOM173("ROOM 173", Floor.FIRST, 800f, 1600f, 3f),
//    ROOM176("ROOM 176", Floor.FIRST, 800f, 1600f, 3f),
//    ROOM178("ROOM 178", Floor.FIRST, 550f, 1600f, 3f),
//    ROOM179("ROOM 179", Floor.FIRST, 550f, 1600f, 3f),
//    ROOM180("ROOM 180", Floor.FIRST, 550f, 1600f, 3f),
//    ROOM181("ROOM 181", Floor.FIRST, 550f, 1600f, 3f),
//    HALL_A("HALL A", Floor.GROUND, 850f, 1400f, 2.5f),
//    CODELAB("CODELAB", Floor.FIRST, 900f, 1400f, 2.5f),
//    AUDITORIUM15("AUDITORIUM 15", Floor.FIRST, 900f, 1200f, 2.5f),
//    AUDITORIUM12("AUDITORIUM 12", Floor.FIRST, 900f, 1400f, 2.5f),
//    AUDITORIUM11("AUDITORIUM 11", Floor.FIRST, 800f, 950f, 2f),
//    AUDITORIUM10("AUDITORIUM 10 (LIGHTNING TALKS)", Floor.FIRST, 800f, 950f, 2f),
//    EXHIBITION("EXHIBITION", Floor.GROUND, 1050f, 750f, 2f);
//
//    companion object {
//        fun forName(location: String): Room? {
//            return entries.firstOrNull { it.title.equals(location, ignoreCase = true) }
//        }
//    }
//}
//
//@OptIn(ExperimentalResourceApi::class)
//@Composable
//fun ColumnScope.RoomMap(room: Room) {
//    val screenSizeIsTooWide = Screen.isTooWide()
//    var svg: Svg? by remember { mutableStateOf(null) }
//
//    val path = room.floor.resource
//    LaunchedEffect(path) {
//        svg = Svg(Res.readBytes(path))
//    }
//
//    Box(
//        Modifier
//            .run {
//                if (screenSizeIsTooWide) {
//                    width(1000.dp)
//                        .height(500.dp)
//                        .align(Alignment.CenterHorizontally)
//                } else {
//                    fillMaxWidth()
//                        .height(343.dp)
//                }
//            }
//            .clipToBounds()
//    ) {
//        Canvas(modifier = Modifier.fillMaxSize()) {
//            val currentSvg = svg ?: return@Canvas
//            val scale = size.width / currentSvg.width
//            val imageHeight = currentSvg.height * scale
//            val offsetY = (size.height - imageHeight) / 2
//
//            translate(0f, offsetY) {
//                scale(scale, pivot = Offset.Zero) {
//                    translate(-room.x, -room.y) {
//                        val zoom = room.zoom
//                        scale(zoom) {
//                            currentSvg.renderTo(this)
//                        }
//                    }
//                }
//            }
//        }
//    }
//}