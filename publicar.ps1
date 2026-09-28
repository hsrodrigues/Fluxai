# Publica uma nova versão do FluxAí: cria a tag v1.0.N e envia ao GitHub.
# O GitHub Actions compila e assina o APK; depois este script baixa o APK e publica no
# site (Firebase Hosting), de onde a página de download e o app pegam a atualização.
# Uso: .\publicar.ps1                 (usa o próximo número depois da última tag)
#      .\publicar.ps1 -Versao 420
#      .\publicar.ps1 -SoSite -Versao 420   (só republica no site uma versão já compilada)
#      .\publicar.ps1 -Opcional     (não bloqueia as versões anteriores; por padrão elas deixam de abrir)
param([int]$Versao = 0, [switch]$SoSite, [switch]$Opcional)
$ErrorActionPreference = "Stop"

if ($Versao -eq 0) {
    # Próximo número depois da maior tag v1.0.N (o version.properties local pode estar atrasado)
    $ultima = git tag --list "v1.0.*" | ForEach-Object { [int]($_ -replace '^v1\.0\.', '') } | Sort-Object | Select-Object -Last 1
    $Versao = [int]$ultima + 1
}
$tag = "v1.0.$Versao"

if (-not $SoSite) {
    if (git status --porcelain -- app/src) {
        Write-Host "Há alterações não commitadas em app/src. Faça o commit antes de publicar." -ForegroundColor Yellow
        exit 1
    }
    git push origin HEAD
    git tag $tag
    git push origin $tag
    Write-Host "Tag $tag enviada. Aguardando o GitHub Actions compilar o APK..." -ForegroundColor Green

    # Espera o workflow da tag aparecer e terminar
    $run = $null
    for ($i = 0; $i -lt 30 -and -not $run; $i++) {
        Start-Sleep -Seconds 5
        $run = gh run list --workflow release.yml --branch $tag -L 1 --json databaseId -q ".[0].databaseId"
    }
    if (-not $run) { Write-Host "Workflow da tag $tag não encontrado." -ForegroundColor Red; exit 1 }
    gh run watch $run --exit-status
    if ($LASTEXITCODE -ne 0) { Write-Host "Build falhou: nada foi publicado no site." -ForegroundColor Red; exit 1 }
}

# Baixa o APK do Release e publica no site junto com o versao.json
$pasta = "site/download"
New-Item -ItemType Directory -Force $pasta | Out-Null
Remove-Item "$pasta/*.apk" -ErrorAction SilentlyContinue
gh release download $tag --pattern "FluxAi.apk" --dir $pasta --clobber
$apk = Get-Item "$pasta/FluxAi.apk"
# Versão mínima aceita pelo app: a nova, ou a anterior se a atualização for opcional
$minima = $Versao
if ($Opcional -and (Test-Path "site/versao.json")) { $minima = (Get-Content "site/versao.json" -Raw | ConvertFrom-Json).minima }
$info = [ordered]@{
    versao    = $Versao
    minima    = [int]$minima
    nome      = "1.0.$Versao"
    apk       = "download/FluxAi.apk"
    tamanhoMb = [math]::Round($apk.Length / 1MB, 1)
    data      = (Get-Date -Format "yyyy-MM-dd")
}
$info | ConvertTo-Json | Set-Content -Encoding utf8NoBOM "site/versao.json"

firebase deploy --only hosting
Write-Host "Versão $tag publicada em https://fluxai-adbdf.web.app" -ForegroundColor Green
