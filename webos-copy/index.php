<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json");

// Serverinizin tam ünvanını buraya yazın
$baseUrl = "http://kanal65.xyz/by-kerimoff-player/msx/";

$response = [
    "name" => "AUREX PLAYER TV",
    "version" => "1.0.0",
    "parameter" => "menu:" . $baseUrl . "menu.php"
];

echo json_encode($response, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
?>
