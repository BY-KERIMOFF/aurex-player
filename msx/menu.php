<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json");

$m3uUrl = "http://kanal65.xyz/by-kerimoff-player/playlist.m3u";
$baseUrl = "http://kanal65.xyz/by-kerimoff-player/msx/";

$m3uContent = file_get_contents($m3uUrl);
if (!$m3uContent) {
    echo json_encode(["error" => "M3U faylı oxunmadı"]);
    exit;
}

$lines = explode("\n", $m3uContent);
$categories = [];

foreach ($lines as $line) {
    if (strpos($line, '#EXTINF') !== false) {
        preg_match('/group-title="([^"]*)"/', $line, $matches);
        $group = !empty($matches[1]) ? $matches[1] : "Digər";
        if (!in_array($group, $categories)) {
            $categories[] = $group;
        }
    }
}

sort($categories);

$menu = [];
$menu[] = [
    "icon" => "home",
    "label" => "Ana Səhifə",
    "data" => $baseUrl . "content.php"
];

$menu[] = ["type" => "separator", "label" => "Kateqoriyalar"];

foreach ($categories as $cat) {
    $menu[] = [
        "icon" => "list",
        "label" => $cat,
        "data" => $baseUrl . "content.php?category=" . urlencode($cat)
    ];
}

$response = [
    "headline" => "AUREX PLAYER",
    "menu" => $menu
];

echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
?>
