
set xdata time
set timefmt "%Y-%m-%d-%H-%M-%S"
set format x "%Y-%m-%d"
set xrange ["2011-01-01-00-00-00":]

plot "out/hsdir-set-instability" using 1:2 with lines title "HSDir set size",\
     "out/hsdir-set-instability" using 1:(0):6 with filledcurves title "4 hr",\
     "out/hsdir-set-instability" using 1:(0):5 with filledcurves title "3 hr",\
     "out/hsdir-set-instability" using 1:(0):4 with filledcurves title "2 hr",\
     "out/hsdir-set-instability" using 1:(0):3 with filledcurves title "1 hr"

