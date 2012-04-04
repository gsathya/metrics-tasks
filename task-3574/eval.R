library(ggplot2)
data <- read.csv("bridge-bandwidth-per-day.csv", stringsAsFactors = FALSE, col.names = c("fingerprint", "date", "operation", "bytes", "intervals"))
d <- aggregate(list(bytes = data$bytes, intervals = data$intervals), by = list(fingerprint = data$fingerprint), sum)
ggplot(d, aes(x = sort(bytes) / 2^30, y = (1:length(bytes)) / length(bytes))) +
geom_line() +
scale_x_continuous(name = "\nTotal read and written GiB per month", limits = c(0, 100)) +
scale_y_continuous(name = "Fraction of bridges\n", formatter = "percent", limits = c(0, 1))
ggplot(d, aes(x = sort(bytes / intervals) / (15 * 60 * 2^10), y = (1:length(bytes)) / length(bytes))) +
geom_line() +
scale_x_continuous(name = "\nMean read and written KiB per second", limits = c(0, 10)) +
scale_y_continuous(name = "Fraction of bridges\n", formatter = "percent", limits = c(0, 1))

