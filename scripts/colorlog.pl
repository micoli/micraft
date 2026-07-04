#!/usr/bin/env perl
use strict; use warnings;
$| = 1;  # pas de buffering, indispensable pour tail -f / docker logs -f

my %color = (
    TRACE => "1;30",
    DEBUG => "36",
    INFO  => "32",
    WARN  => "33",
    ERROR => "1;31",
);

while (<STDIN>) {
    # container "micraft-1 |" -> gris
    s/^(\S+\s*\|\s*)/\e[2m$1\e[0m/;

    # tous les groupes [xxx] de la ligne :
    # le 1er = service ([server]/[webpack]) -> magenta
    # les suivants = subService ([main], [HPM], etc.) -> bleu
    my $bracket_seen = 0;
    s/(\[[^\[\]]+\])/$bracket_seen++ ? "\e[34m$1\e[0m" : "\e[35m$1\e[0m"/ge;

    # timestamp -> vert
    s/(\d{2}:\d{2}:\d{2}\.\d{3})/\e[32m$1\e[0m/;

    # niveau de log -> couleur selon sévérité
    s/\b(TRACE|DEBUG|INFO|WARN|ERROR)\b/"\e[".$color{$1}."m$1\e[0m"/e;

    print;
}