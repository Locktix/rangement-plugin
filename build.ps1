# Compile le plugin Rangement avec les jars de Paper deja presents dans le serveur.
# Usage : powershell -File build.ps1 [-Installer] [-SansTests]
param([switch]$Installer, [switch]$SansTests)
$ErrorActionPreference = 'Stop'
$ici = $PSScriptRoot
$serveur = Split-Path $ici -Parent
$jar = 'Rangement-1.0.0.jar'
Set-Location $ici

# libraries du serveur + lib\ (dependances de compilation seulement, ex : annotations JetBrains)
$cp = (@(Get-ChildItem "$serveur\libraries" -Recurse -Filter *.jar) + @(Get-ChildItem "$ici\lib" -Filter *.jar -ErrorAction SilentlyContinue) | ForEach-Object FullName) -join ';'
if (Test-Path build) { Remove-Item build -Recurse -Force }
New-Item -ItemType Directory -Force build\classes | Out-Null
$sources = Get-ChildItem src\main\java -Recurse -Filter *.java | ForEach-Object FullName
& javac --release 25 -encoding UTF-8 -Xlint:deprecation -cp $cp -d build\classes @sources
if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation" }
Copy-Item src\main\resources\* build\classes\

# Tests JUnit + MockBukkit (src\test\java), lances avant de creer le jar. -SansTests pour les sauter.
# Les jars de test listes dans lib\test\dependances.txt sont telecharges une fois dans lib\test\jars.
if (-not $SansTests -and (Test-Path src\test\java)) {
    $dossierTests = "$ici\lib\test\jars"
    New-Item -ItemType Directory -Force $dossierTests | Out-Null
    $jarsTests = foreach ($ligne in Get-Content "$ici\lib\test\dependances.txt") {
        $ligne = $ligne.Trim()
        if ($ligne -eq '' -or $ligne.StartsWith('#')) { continue }
        $g, $a, $v = $ligne.Split(':')
        $fichier = "$dossierTests\$a-$v.jar"
        if (-not (Test-Path $fichier)) {
            $url = "https://repo1.maven.org/maven2/$($g.Replace('.', '/'))/$a/$v/$a-$v.jar"
            Write-Host "Telechargement de $a-$v.jar"
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $client = New-Object Net.WebClient
            $client.DownloadFile($url, "$fichier.part")
            $attendu = $client.DownloadString("$url.sha1").Trim().Split(' ')[0]
            $obtenu = (Get-FileHash "$fichier.part" -Algorithm SHA1).Hash
            if ($obtenu -ne $attendu) { Remove-Item "$fichier.part"; throw "Somme SHA-1 incorrecte pour $a-$v.jar" }
            Move-Item "$fichier.part" $fichier
        }
        $fichier
    }
    $junit = $jarsTests | Where-Object { $_ -like '*junit-platform-console-standalone-*' } | Select-Object -First 1
    $cpTests = "$cp;build\classes;" + ($jarsTests -join ';')
    New-Item -ItemType Directory -Force build\test-classes | Out-Null
    $tests = @(Get-ChildItem src\test\java -Recurse -Filter *.java | ForEach-Object FullName)
    & javac --release 25 -encoding UTF-8 -cp $cpTests -d build\test-classes @tests
    if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation des tests" }
    & java -jar $junit execute --disable-banner --details=summary --details-theme=ascii `
        --class-path "$cpTests;build\test-classes" --scan-class-path build\test-classes
    if ($LASTEXITCODE -ne 0) { throw "Des tests echouent : jar non cree" }
}
& jar --create --file "build\$jar" -C build\classes .
if ($LASTEXITCODE -ne 0) { throw "Echec de la creation du jar" }
Write-Host "OK -> $ici\build\$jar"

if ($Installer) {
    Copy-Item "build\$jar" "$serveur\plugins\$jar" -Force
    Write-Host "Copie dans $serveur\plugins (redemarre le serveur)"
}
