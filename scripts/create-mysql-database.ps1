param(
    [string]$DatabaseName = "bilibrains",
    [string]$DatabaseHost = "localhost",
    [int]$Port = 3306,
    [string]$Username = "root",
    [string]$Password = $env:MYSQL_PASSWORD
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not ($DatabaseName -match "^[A-Za-z0-9_]+$")) {
    throw "Invalid database name: $DatabaseName"
}

if ([string]::IsNullOrWhiteSpace($Password)) {
    throw "Provide -Password or set MYSQL_PASSWORD before running this script."
}

$connectorJar = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j" -Recurse -Filter "mysql-connector-j-*.jar" |
    Sort-Object FullName -Descending |
    Select-Object -First 1

if (-not $connectorJar) {
    throw "MySQL Connector/J was not found under $env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j"
}

$jdbcUrl = "jdbc:mysql://{0}:{1}/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" -f $DatabaseHost, $Port
$sql = "CREATE DATABASE IF NOT EXISTS $DatabaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

$jshellScript = @"
import java.sql.*;
Class.forName("com.mysql.cj.jdbc.Driver");
try (Connection connection = DriverManager.getConnection("$jdbcUrl", "$Username", "$Password");
     Statement statement = connection.createStatement()) {
    statement.execute("$sql");
    System.out.println("READY:$DatabaseName");
}
/exit
"@

$output = $jshellScript | jshell --class-path "$($connectorJar.FullName)"
$outputText = ($output | Out-String).Trim()

if ($LASTEXITCODE -ne 0) {
    throw "jshell exited with code $LASTEXITCODE"
}

if ($outputText -notmatch [regex]::Escape("READY:$DatabaseName")) {
    throw "Database creation did not report success. Output:`n$outputText"
}

Write-Output "Database '$DatabaseName' is ready."
