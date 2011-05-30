library(ggplot2)

data <- read.csv("mtbf-sim.csv", stringsAsFactors = FALSE)
d <- data[data$time >= '2010' & data$time < '2011', ]
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)), mean)
d <- rbind(
  data.frame(x = d$wmtbf30, y = d$perc90tunf30, sim = "30 %"),
  data.frame(x = d$wmtbf40, y = d$perc90tunf40, sim = "40 %"),
  data.frame(x = d$wmtbf50, y = d$perc90tunf50, sim = "50 % (default)"),
  data.frame(x = d$wmtbf60, y = d$perc90tunf60, sim = "60 %"),
  data.frame(x = d$wmtbf70, y = d$perc90tunf70, sim = "70 %"))
ggplot(d, aes(x = x / (24 * 60 * 60), y = y / (60 * 60))) +
facet_wrap(~ sim) +
geom_path() +
scale_x_continuous("\nRequired WMTBF in days",
  breaks = seq(0, max(d$x, na.rm = TRUE) / (24 * 60 * 60), 7),
  minor = seq(0, max(d$x, na.rm = TRUE) / (24 * 60 * 60), 1)) +
scale_y_continuous(paste("Time in hours until 10 % of relays\nor ",
  "27.1 % of streams have failed\n", sep = ""),
  breaks = seq(0, max(d$y, na.rm = TRUE) / (60 * 60), 24))
ggsave(filename = "mtbf-sim.pdf", width = 8, height = 5, dpi = 100)

## Commented out, because this graph is meaningless in b/w.  The graph
## above contains the same data, but can be printed in b/w.
#data <- read.csv("mtbf-sim.csv", stringsAsFactors = FALSE)
#d <- data[data$time >= '2010' & data$time < '2011', ]
#d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)), mean)
#d <- rbind(
#  data.frame(x = d$wmtbf70, y = d$perc90tunf70, sim = "70 %"),
#  data.frame(x = d$wmtbf60, y = d$perc90tunf60, sim = "60 %"),
#  data.frame(x = d$wmtbf50, y = d$perc90tunf50, sim = "50 % (default)"),
#  data.frame(x = d$wmtbf40, y = d$perc90tunf40, sim = "40 %"),
#  data.frame(x = d$wmtbf30, y = d$perc90tunf30, sim = "30 %"))
#ggplot(d, aes(x = x / (24 * 60 * 60), y = y / (60 * 60),
#  colour = sim)) +
#geom_path() +
#scale_x_continuous("\nRequired WMTBF in days",
#  breaks = seq(0, max(d$x, na.rm = TRUE) / (24 * 60 * 60), 7),
#  minor = seq(0, max(d$x, na.rm = TRUE) / (24 * 60 * 60), 1)) +
#scale_y_continuous(paste("Time until    \n10 % of relays or    \n",
#  "27.1 % of streams    \nhave failed    \nin hours    ", sep = ""),
#  breaks = seq(0, max(d$y, na.rm = TRUE) / (60 * 60), 24)) +
#scale_colour_hue("Fraction of relays\nby highest WMTBF",
#  breaks = c("30 %", "40 %", "50 % (default)", "60 %", "70 %")) +
#opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
#  hjust = 0.5),
#  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
#  hjust = 1))
#ggsave(filename = "mtbf-sim.pdf", width = 8, height = 5, dpi = 100)

## Commented out, because focusing on the development over time is the
## wrong thing here.
#simulations <- paste("mtunf", c(20, 30, 40, 50, 60, 70, 80),
#  sep = "")
#d <- data[data$time >= '2010' & data$time < '2011',
#  c("time", simulations)]
#d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)), mean)
#d <- melt(d, id.vars = 1)
#ggplot(d, aes(x = date, y = value / (24 * 60 * 60), colour = variable)) +
#geom_line() +
#scale_x_date("", major = "3 months", minor = "1 month",
#  format = "%b %Y") +
#scale_y_continuous(paste("Mean time    \nuntil next    \nfailure    \n",
#  "in days    \n", sep = ""),
#  limits = c(0, max(d$value, na.rm = TRUE) / (24 * 60 * 60))) +
#scale_colour_hue(paste("Percentile\nhighest\nweighted mean\n",
#  "time between\nfailures", sep = ""), breaks = simulations,
#  labels = paste(substr(simulations, 6, 9),
#  ifelse(simulations == "mtunf50", "(default)", ""))) +
#opts(axis.title.y = theme_text(size = 12 * 0.8, face = "bold",
#  vjust = 0.5, hjust = 1))
#ggsave(filename = "mtbf-sim1.pdf", width = 8, height = 5, dpi = 100)

