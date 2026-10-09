#!/bin/bash
# usage: build.sh <supabase-script-file> <out>
SUPA=${1:-vendor/supabase-js-2.117.2.html}; OUT=$(realpath -m "${2:-standalone/salakot-pos.html}"); cd "$(dirname "$0")/src"
LOGO="%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 128 128'%3E%3Crect width='128' height='128' rx='28' fill='%2315233b'/%3E%3Cpath d='M64 24 16 86c16 11 80 11 96 0z' fill='%23e0a43a'/%3E%3Cpath d='M64 24 42 90M64 24l22 66M64 24v68' stroke='%238a5d12' stroke-width='3.5' fill='none'/%3E%3Cpath d='M16 86c16 11 80 11 96 0' stroke='%232f6fd6' stroke-width='9' fill='none' stroke-linecap='round'/%3E%3Ccircle cx='64' cy='24' r='7' fill='%232f6fd6'/%3E%3C/svg%3E"
{
cat <<EOF
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>SalakotPOS</title>
<link rel="icon" href="data:image/svg+xml;utf8,$LOGO">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600;12..96,700;12..96,800&family=Figtree:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
EOF
sed 's#/\* ---------- Quilev additions ---------- \*/#/* ---------- shared additions ---------- */#' style.css
cat extra.css
cat <<EOF
</style>
</head>
<body>
<div id="app"><div class="loading"><div><img alt="" src="data:image/svg+xml;utf8,$LOGO"><p>Opening SalakotPOS…</p></div></div></div>
<div id="modal"></div>
<div id="toast"></div>
<script>window.SB_CFG = { url: "https://osllqdmaxurdvqjocbab.supabase.co", key: "sb_publishable_EHimT2l3dYpw5rwwBx_U0w_sdWvma8c" };</script>
EOF
cat "../$SUPA"
echo '<script>'; echo '"use strict";'
cat base.js app1.js app2.js app3.js app4.js
echo '</script>'; echo '</body>'; echo '</html>'
} > $OUT
