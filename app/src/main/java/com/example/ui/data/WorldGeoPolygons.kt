package com.example.ui.data

/**
 * Simplified geographic vector coordinates (latitude, longitude) for rendering
 * authentic cartographic coastlines, Southeast Asia landmasses, Thailand border silhouette,
 * and tectonic fault lines on the interactive Web Mercator map canvas.
 */
object WorldGeoPolygons {

    // Detailed Thailand + Malay Peninsula outline (lat, lon)
    val THAILAND_OUTLINE: List<Pair<Double, Double>> = listOf(
        20.46 to 99.88, 20.15 to 100.55, 19.55 to 101.25, 18.45 to 101.05,
        18.10 to 102.25, 18.42 to 103.65, 17.65 to 104.55, 16.55 to 104.75,
        15.40 to 105.62, 14.35 to 105.18, 14.15 to 103.25, 13.05 to 102.50,
        12.15 to 102.75, 12.60 to 101.40, 13.48 to 100.55, 13.30 to 100.00,
        12.50 to 99.95, 11.45 to 99.62, 10.45 to 99.22, 9.20 to 99.55,
        8.45 to 100.12, 7.20 to 100.62, 6.25 to 102.08, 5.75 to 101.10,
        6.45 to 100.15, 7.20 to 99.50, 8.10 to 98.30, 9.85 to 98.60,
        11.20 to 99.20, 13.30 to 99.25, 15.20 to 98.45, 16.70 to 98.55,
        17.90 to 97.75, 19.35 to 97.95, 20.46 to 99.88
    )

    // Mainland Southeast Asia + Southern China + India + East Asia coastline
    val EURASIA_SE_COAST: List<Pair<Double, Double>> = listOf(
        35.0 to 70.0, 24.5 to 67.0, 21.0 to 72.5, 15.0 to 74.0, 8.1 to 77.5,
        13.0 to 80.3, 19.5 to 85.0, 22.0 to 91.8, 16.5 to 94.3, 16.2 to 97.5,
        10.0 to 98.5, 6.5 to 99.8, 1.3 to 103.8, 4.5 to 103.5, 7.5 to 100.5,
        13.5 to 100.5, 12.0 to 102.8, 10.5 to 104.5, 8.6 to 105.1, 12.5 to 109.4,
        16.1 to 108.3, 20.8 to 106.8, 22.5 to 114.2, 25.0 to 119.5, 31.2 to 121.8,
        36.0 to 120.5, 39.0 to 118.0, 38.0 to 126.5, 35.1 to 129.1, 41.0 to 129.8,
        45.0 to 136.5, 54.0 to 140.0, 62.0 to 165.0, 68.0 to 175.0, 72.0 to 120.0,
        70.0 to 60.0, 60.0 to 30.0, 54.0 to 10.0, 44.0 to -8.0, 36.5 to -5.5,
        37.0 to 15.0, 31.5 to 34.5, 29.5 to 32.5, 12.8 to 45.0, 25.0 to 56.5,
        24.5 to 67.0
    )

    // Major Islands (Sumatra, Java, Borneo, Philippines, Japan, Australia, Sri Lanka, Taiwan)
    val ISLAND_POLYGONS: List<List<Pair<Double, Double>>> = listOf(
        // Sumatra
        listOf(5.5 to 95.3, 3.5 to 99.0, 0.5 to 103.2, -3.5 to 105.8, -5.8 to 105.7, -4.0 to 102.2, 1.0 to 97.8, 5.5 to 95.3),
        // Java
        listOf(-6.1 to 105.8, -6.9 to 110.4, -7.8 to 114.4, -8.5 to 114.2, -8.0 to 110.0, -7.1 to 106.4, -6.1 to 105.8),
        // Borneo
        listOf(6.8 to 116.8, 5.8 to 118.8, 1.2 to 118.0, -3.5 to 116.0, -3.2 to 111.5, 1.5 to 109.5, 4.5 to 114.0, 6.8 to 116.8),
        // Sulawesi
        listOf(1.6 to 125.0, -1.5 to 123.0, -4.8 to 122.5, -5.4 to 119.5, -1.0 to 119.2, 1.2 to 121.0, 1.6 to 125.0),
        // Philippines (Luzon + Mindanao simplified)
        listOf(18.5 to 121.5, 16.0 to 122.3, 13.5 to 124.0, 14.0 to 120.5, 16.5 to 120.2, 18.5 to 121.5),
        listOf(9.8 to 125.5, 7.0 to 126.4, 5.6 to 125.4, 6.9 to 122.0, 8.8 to 123.5, 9.8 to 125.5),
        // Taiwan
        listOf(25.2 to 121.5, 22.0 to 120.8, 23.5 to 120.1, 25.2 to 121.5),
        // Japan
        listOf(31.5 to 130.5, 34.5 to 136.8, 36.0 to 140.5, 41.2 to 141.3, 44.5 to 145.0, 43.0 to 140.5, 37.5 to 136.8, 33.8 to 130.5, 31.5 to 130.5),
        // Sri Lanka
        listOf(9.8 to 80.2, 7.5 to 81.8, 6.0 to 80.5, 7.8 to 79.8, 9.8 to 80.2),
        // Australia
        listOf(-12.5 to 130.8, -12.0 to 136.8, -16.5 to 136.5, -11.0 to 142.5, -20.0 to 149.0, -28.0 to 153.5, -37.5 to 150.0, -38.5 to 144.5, -35.0 to 135.5, -32.0 to 126.0, -34.0 to 115.0, -22.0 to 114.0, -17.0 to 122.2, -12.5 to 130.8),
        // Africa
        listOf(35.8 to -5.5, 37.0 to 10.5, 31.2 to 32.3, 12.0 to 51.0, -1.5 to 41.5, -26.0 to 33.0, -34.8 to 20.0, -18.0 to 11.8, 4.5 to 9.5, 5.0 to -7.5, 15.0 to -17.5, 35.8 to -5.5),
        // North America
        listOf(70.0 to -160.0, 70.0 to -80.0, 52.0 to -55.0, 41.0 to -70.0, 25.5 to -80.2, 29.0 to -90.0, 19.0 to -96.0, 10.0 to -84.0, 15.0 to -93.0, 23.0 to -106.0, 32.5 to -117.0, 48.0 to -125.0, 60.0 to -150.0, 70.0 to -160.0),
        // South America
        listOf(10.5 to -75.0, 10.0 to -62.0, 0.0 to -50.0, -6.0 to -35.0, -23.0 to -43.0, -35.0 to -57.0, -54.0 to -68.0, -45.0 to -74.5, -18.0 to -70.5, -5.0 to -81.0, 10.5 to -75.0)
    )

    // Major Tectonic Fault & Subduction Lines impacting Thailand & Asia-Pacific
    val TECTONIC_FAULT_LINES: List<List<Pair<Double, Double>>> = listOf(
        // Sumatra - Andaman Subduction Trench
        listOf(-8.8 to 114.5, -7.2 to 105.0, -2.5 to 99.0, 3.5 to 94.5, 9.5 to 92.2, 14.5 to 93.5),
        // Sagaing Fault (Myanmar -> Northern Thailand proximity)
        listOf(25.5 to 96.5, 22.0 to 96.1, 19.5 to 96.2, 16.5 to 96.5, 14.0 to 96.8),
        // Mae Chan & Phayao Fault approximation
        listOf(20.4 to 99.4, 20.1 to 100.3),
        listOf(19.6 to 99.5, 19.1 to 99.9, 18.8 to 100.2),
        // Pacific Ring of Fire (Philippines -> Taiwan -> Japan)
        listOf(5.0 to 127.0, 12.0 to 125.5, 18.5 to 122.8, 24.0 to 122.0, 31.0 to 132.0, 36.0 to 142.0, 44.0 to 148.5)
    )

    // Major River Flood Corridors in Thailand (Chao Phraya & Mekong border)
    val MAJOR_RIVER_CORRIDORS: List<List<Pair<Double, Double>>> = listOf(
        // Chao Phraya
        listOf(15.70 to 100.12, 15.18 to 100.12, 14.35 to 100.56, 13.75 to 100.50, 13.54 to 100.59),
        // Mekong along Thailand border
        listOf(20.35 to 100.08, 19.85 to 100.55, 18.20 to 102.10, 18.30 to 103.65, 17.40 to 104.78, 15.32 to 105.50)
    )
}
