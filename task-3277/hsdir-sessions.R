library(ggplot2)
data <- read.csv("hsdir-sessions.csv", stringsAsFactors = FALSE)

## Histogram; not that useful
#ggplot(data, aes(x = duration / 3600)) +
#geom_histogram(aes(y = ..density..), binwidth = 1) +
#scale_x_continuous(limits = c(0, 72)) +
#scale_y_continuous(formatter = "percent")

data <- sort(data$duration)
data <- data.frame(x = data / (60 * 60),
  y = (length(data):1) / length(data))
ggplot(data, aes(x = x, y = y)) +
geom_line() +
scale_y_continuous("Cumulative fraction of continuous HSDir sessions\n",
  formatter = "percent", limits = c(0, 1)) +
scale_x_continuous(paste("\nHSDir session time between the relay earning",
  "the HSDir flag and going away in hours"),
  limits = c(0, 3 * 24), breaks = seq(0, 3 * 24, 24))
ggsave(filename = "hsdir-sessions.png", width = 8, height = 5, dpi = 72)

