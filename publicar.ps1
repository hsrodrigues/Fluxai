# Publica uma nova versão do FluxAí: cria a tag v1.0.N e envia ao GitHub.
# O GitHub Actions compila o APK e cria o Release; o app avisa os usuários sozinho.
# Uso: .\publicar.ps1          (usa o próximo número de version.properties)
#      .\publicar.ps1 -Versao 410
param([int]$Versao = 0)
$ErrorActionPreference = "Stop"

if ($Versao -eq 0) {
    $linha = Select-String -Path "app/version.properties" -Pattern "^VERSION_CODE=(\d+)"
    $Versao = [int]$linha.Matches[0].Groups[1].Value
}
$tag = "v1.0.$Versao"

if (git status --porcelain -- app/src) {
    Write-Host "Há alterações não commitadas em app/src. Faça o commit antes de publicar." -ForegroundColor Yellow
    exit 1
}

git push origin HEAD
git tag $tag
git push origin $tag
Write-Host "Tag $tag enviada. Acompanhe em https://github.com/hsrodrigues/Fluxai/actions" -ForegroundColor Green
