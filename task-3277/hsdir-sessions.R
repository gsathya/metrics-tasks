library(ggplot2)
data <- read.csv("hsdir-sessions.csv", stringsAsFactors = FALSE)

## Histogram; not that useful
#ggplot(data, aes(x = duration / 3600)) +
#geom_histogram(aes(y = ..density..), binwidth = 1) +
#scale_x_continuous(limits = c(0, 72)) +
#scale_y_continuous(formatter = "percent")

# We want to compare actual session times to...
hour24 <- sort(data$duration)

# ... simulated session times if HSDir flags would have been assigned
# after 25 or 26 hours, not 24.
hour25 <- data[data$duration >= 3600, "duration"]
hour25 <- sort(hour25) - 3600
hour26 <- data[data$duration >= 7200, "duration"]
hour26 <- sort(hour26) - 7200

data <- rbind(
  data.frame(x = hour24 / (60 * 60),
  y = (length(hour24):1) / length(hour24),
  type = "24 hours (implemented)"),
  data.frame(x = hour25 / (60 * 60),
  y = (length(hour25):1) / length(hour25),
  type = "25 hours (simulated)"),
  data.frame(x = hour26 / (60 * 60),
  y = (length(hour26):1) / length(hour26),
  type = "26 hours (simulated)"))

ggplot(data[data$type == "24 hours (implemented)", ], aes(x = x, y = y)) +
geom_line() +
scale_y_continuous("Cumulative fraction of continuous HSDir sessions\n",
  formatter = "percent", limits = c(0, 1)) +
scale_x_continuous(paste("\nHSDir session time between the relay earning",
  "the HSDir flag and going away in hours"),
  limits = c(0, 3 * 24), breaks = seq(0, 3 * 24, 24))
ggsave(filename = "hsdir-sessions.png", width = 8, height = 5, dpi = 72)

ggplot(data, aes(x = x, y = y, colour = type)) +
geom_line() +
scale_y_continuous("Cumulative fraction of continuous HSDir sessions\n",
  formatter = "percent", limits = c(0, 1)) +
scale_x_continuous(paste("\nHSDir session time between the relay earning",
  "the HSDir flag and going away in hours"),
  limits = c(0, 3 * 24), breaks = seq(0, 3 * 24, 24)) +
scale_colour_hue("HSDir flag assignment after") +
opts(legend.position = "top")
ggsave(filename = "hsdir-sessions-sim.png", width = 8, height = 5,
  dpi = 72)

