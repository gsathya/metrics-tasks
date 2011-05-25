
set xdata time
set timefmt "%Y-%m-%d-%H-%M-%S"
set format x "%Y-%m"
set xrange ["2011-01-01-00-00-00":]
set yrange [0:1]

plot "out/hsdir-set-instability" using 1:(0):($6/$2) with filledcurves title "4 hr",\
     "out/hsdir-set-instability" using 1:(0):($5/$2) with filledcurves title "3 hr",\
     "out/hsdir-set-instability" using 1:(0):($4/$2) with filledcurves title "2 hr",\
     "out/hsdir-set-instability" using 1:(0):($3/$2) with filledcurves title "1 hr"

