<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json");

$m3uUrl = "http://kanal65.xyz/by-kerimoff-player/playlist.m3u";
$categoryFilter = isset($_GET['category']) ? $_GET['category'] : "";

$m3uContent = file_get_contents($m3uUrl);
$lines = explode("\n", $m3uContent);

$channels = [];
$currentItem = null;

foreach ($lines as $line) {
    $line = trim($line);
    if (empty($line)) continue;

    if (strpos($line, '#EXTINF') !== false) {
        preg_match('/group-title="([^"]*)"/', $line, $matches_group);
        preg_match('/tvg-logo="([^"]*)"/', $line, $matches_logo);

        $group = !empty($matches_group[1]) ? $matches_group[1] : "Digər";
        $logo = !empty($matches_logo[1]) ? $matches_logo[1] : "";

        $nameParts = explode(',', $line);
        $name = trim(end($nameParts));

        $currentItem = [
            "name" => $name,
            "group" => $group,
            "logo" => $logo
        ];
    } elseif (strpos($line, 'http') === 0 && $currentItem !== null) {
        $currentItem['url'] = $line;

        // Kateqoriya filtrinə uyğundursa əlavə et
        if (empty($categoryFilter) || $currentItem['group'] === $categoryFilter) {
            $channels[] = $currentItem;
        }
        $currentItem = null;
    }
}

$items = [];
foreach ($channels as $channel) {
    $items[] = [
        "title" => $channel['name'],
        "image" => $channel['logo'],
        "action" => "video:" . $channel['url'],
        "type" => "default",
        "layout" => "0,0,2,2" // Grid ölçüsü
    ];
}

$response = [
    "pages" => [
        [
            "items" => $items
        ]
    ]
];

echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
?>
