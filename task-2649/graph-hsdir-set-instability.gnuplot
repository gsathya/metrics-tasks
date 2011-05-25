
set xdata time
set timefmt "%Y-%m-%d-%H-%M-%S"
set xrange ["2011-01-01-00-00-00":]
set yrange [0:1]

plot "out/hsdir-set-instability" using 1:($3/$2) with lines title "1 hr",\
     "out/hsdir-set-instability" using 1:($4/$2) with lines title "2 hr",\
     "out/hsdir-set-instability" using 1:($5/$2) with lines title "3 hr",\
     "out/hsdir-set-instability" using 1:($6/$2) with lines title "4 hr"

