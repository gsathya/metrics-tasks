library(ggplot2)
data <- read.csv("onion-key-lifetimes.csv",
  col.names = c("fingerprint", "start", "end", "length"),
  stringsAsFactors = FALSE)
print(data[data$length >= 10 * 24 * 60 * 60, ])
data <- sort(data$length)
data <- data.frame(x = data / (24 * 60 * 60), y = 1:length(data))
ggplot(data, aes(x = x, y = y)) +
geom_line() +
scale_y_continuous("Cumulative number of onion keys\n") +
scale_x_continuous(paste("\nTime between first and last publication of",
  "the same onion key in May 2011 in days"))
ggsave(filename = "onion-key-lifetimes.png", width = 8, height = 5,
  dpi = 72)

