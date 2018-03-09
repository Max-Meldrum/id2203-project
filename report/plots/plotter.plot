set title "Leader Lease Benchmark"
set terminal pdf enhanced
set output 'lease.pdf'

set ylabel 'Seconds'
set yrange [0:*];

set style data histogram
set style histogram cluster gap 4
set style fill solid border -1
set boxwidth 0.9
set format x '%+-.6f' # to make the labels longer
set xtics rotate by 25 right


set auto x
plot 'lease' using 2:xtic(1) title col, \
         '' using 3:xtic(1) title col, \
