library(ggplot2)
library(scales)
library(grid)
library(reshape)
stability <- read.csv("stability.csv", stringsAsFactors = FALSE)

d <- stability[stability$time > '2010-07' & stability$time < '2011-07', ]
d <- d[, c("time", "running")]
d <- na.omit(d)
d_mean <- aggregate(list(running = d$running),
  by = list(date = as.Date(d$time)), quantile, probs = 0.5)
d_max <- aggregate(list(running = d$running),
  by = list(date = as.Date(d$time)), quantile, probs = 0.75)
d_min <- aggregate(list(running = d$running),
  by = list(date = as.Date(d$time)), quantile, probs = 0.25)
d <- data.frame(x = d_mean$date, y = d_mean$running, ymin = d_min$running,
  ymax = d_max$running)
d <- rbind(d,
  data.frame(x = as.Date(setdiff(seq(from = min(d$x, na.rm = TRUE),
  to = max(d$x, na.rm = TRUE), by="1 day"), d$x), origin = "1970-01-01"),
  y = NA, ymin = NA, ymax = NA))
ggplot(d, aes(x = as.Date(x), y = y, ymin = ymin, ymax = ymax)) +
geom_line() +
scale_x_date("", breaks = "3 months", minor_breaks = "1 month",
  labels = date_format("%b %Y")) +
scale_y_continuous(name = "Running    \nbridges    ",
  limits = c(0, max(d_mean$running, na.rm = TRUE))) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "runningbridge.pdf", width = 7, height = 3, dpi = 100)

pdf("runningbridge-detail.pdf", width = 7, height = 4)
grid.newpage()
pushViewport(viewport(layout = grid.layout(2, 1)))
d <- stability[stability$time > '2010-07-10' &
  stability$time < '2010-07-31', ]
a <- ggplot(d, aes(x = as.POSIXct(time), y = running)) +
geom_point(size = 0.75) +
scale_x_datetime(name = "", breaks = "1 week", minor_breaks = "1 day",
  labels = date_format("%b %d, %Y")) +
scale_y_continuous(name = "Running    \nbridges    ",
  limits = c(0, max(d$running, na.rm = TRUE))) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1),
  legend.position = "none")
d <- stability[stability$time > '2011-01-24' &
  stability$time < '2011-02-19', ]
e <- read.csv("stale-bridge-tarballs.csv", stringsAsFactors = FALSE,
  col.names = c("time"))
d <- rbind(
  data.frame(time = d$time, running = d$running, colour = "black"),
  data.frame(time = e$time, running = 687, colour = "grey"))
b <- ggplot(d, aes(x = as.POSIXct(time), y = running, colour = colour)) +
geom_point(size = 0.75) +
scale_x_datetime(name = "", breaks = "1 week", minor_breaks = "1 day",
  labels = date_format("%b %d, %Y")) +
scale_y_continuous(name = "Running    \nbridges    ",
  limits = c(0, max(d$running, na.rm = TRUE))) +
scale_colour_manual(values = c("black", "grey60")) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1),
  legend.position = "none")
print(a, vp = viewport(layout.pos.row = 1, layout.pos.col = 1))
print(b, vp = viewport(layout.pos.row = 2, layout.pos.col = 1))
dev.off()

d <- stability[stability$time > '2010-07' & stability$time < '2011-07', ]
d <- d[, c("time", "minwmtbaca50wmtbac", "minwta", "minwfua50wfu")]
d <- na.omit(d)
d_mean <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  quantile, probs = 0.5)
d_max <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  quantile, probs = 0.75)
d_min <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  quantile, probs = 0.25)
d <- rbind(
  data.frame(x = d_mean$date,
    y = d_mean$minwmtbaca50wmtbac / (24 * 60 * 60),
    ymin = d_min$minwmtbaca50wmtbac / (24 * 60 * 60),
    ymax = d_max$minwmtbaca50wmtbac / (24 * 60 * 60),
    var = "Median WMTBAC"),
  data.frame(x = d_mean$date, y = d_mean$minwta / (24 * 60 * 60),
    ymin = d_min$minwta / (24 * 60 * 60),
    ymax = d_max$minwta / (24 * 60 * 60),
    var = "12.5th perc. WT"),
  data.frame(x = d_mean$date, y = d_mean$minwfua50wfu / 10000,
    ymin = d_min$minwfua50wfu / 10000,
    ymax = d_max$minwfua50wfu / 10000,
    var = "Median WFU"))
missing_dates <- as.Date(setdiff(seq(from = min(d$x, na.rm = TRUE),
  to = max(d$x, na.rm = TRUE), by="1 day"), d$x), origin = "1970-01-01")
d <- rbind(d,
  data.frame(x = missing_dates, y = NA, ymin = NA, ymax = NA,
    var = "Median WMTBAC"),
  data.frame(x = missing_dates, y = NA, ymin = NA, ymax = NA,
    var = "12.5th perc. WT"),
  data.frame(x = missing_dates, y = NA, ymin = NA, ymax = NA,
    var = "Median WFU"))
e <- data.frame(
  yintercept = c(30, 8, 0.98),
  var = c("Median WMTBAC", "12.5th perc. WT", "Median WFU"))
ggplot(d, aes(x = as.Date(x), y = y, ymin = ymin, ymax = ymax)) +
geom_line() +#colour = "grey30") +
#geom_ribbon(alpha = 0.3) +
geom_hline(data = e, aes(yintercept = yintercept), colour = "gray40",
  linetype = 2) +
facet_grid(var ~ ., scales = "free_y") +
scale_x_date("", breaks = "3 months", minor_breaks = "1 month",
  labels = date_format("%b %Y")) +
scale_y_continuous(name = "") +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "requirements.pdf", width = 7, height = 5, dpi = 100)

d <- stability[stability$time > '2010-07' & stability$time < '2011-07', ]
d <- d[, c("time", "perc10wfu0wfu0wmtbac", "perc10wfu0wfu50wmtbac",
  "perc10wfu50wfu0wmtbac", "perc10wfu50wfu50wmtbac")]
d <- na.omit(d)
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  quantile, probs = 0.5)
d <- rbind(d,
  data.frame(date = as.Date(setdiff(seq(from = min(d$date),
  to = max(d$date), by="1 day"), d$date), origin = "1970-01-01"),
  perc10wfu0wfu0wmtbac = NA, perc10wfu0wfu50wmtbac = NA,
  perc10wfu50wfu0wmtbac = NA, perc10wfu50wfu50wmtbac = NA))
d <- melt(d, id = "date")
ggplot(d, aes(x = date, y = value / 10000, colour = variable)) +
geom_line() +
scale_y_continuous(name = paste("10th perc.   \nWFU in   \n",
  "the future   ", sep = ""), labels = percent, limits = c(0, 1)) +
scale_x_date("", breaks = "3 months", minor_breaks = "1 month",
  labels = date_format("%b %Y")) +
scale_colour_manual(name = paste("Requirements for\nconsidering",
  "a\nbridge as stable\n"), breaks = c("perc10wfu50wfu50wmtbac",
  "perc10wfu50wfu0wmtbac", "perc10wfu0wfu50wmtbac",
  "perc10wfu0wfu0wmtbac"), labels = c("WFU & WMTBAC", "WFU", "WMTBAC",
  "None"), values = c("black", "grey60", "grey80", "grey45")) +
opts(plot.title = theme_text(size = 14 * 0.8, face = "bold"),
  axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "fwfu-sim.pdf", width = 7, height = 3, dpi = 100)

d <- stability[stability$time > '2010-07' & stability$time < '2011-07', ]
d <- d[, c("time", "perc10tosa0wfu0wmtbac", "perc10tosa0wfu50wmtbac",
  "perc10tosa50wfu0wmtbac", "perc10tosa50wfu50wmtbac")]
d <- na.omit(d)
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  quantile, probs = 0.5)
d <- rbind(d,
  data.frame(date = as.Date(setdiff(seq(from = min(d$date),
  to = max(d$date), by="1 day"), d$date), origin = "1970-01-01"),
  perc10tosa0wfu0wmtbac = NA, perc10tosa0wfu50wmtbac = NA,
  perc10tosa50wfu0wmtbac = NA, perc10tosa50wfu50wmtbac = NA))
d <- melt(d, id = "date")
ggplot(d, aes(x = date, y = value / 86400, colour = variable)) +
geom_line() +
scale_y_continuous(name = paste("10th perc.   \ntime on   \nthe same   \n",
  "address   \nin days   ", sep = ""),
  breaks = seq(0, max(d$value / 86400, na.rm = TRUE), 7),
  minor = seq(0, max(d$value / 86400, na.rm = TRUE), 1),
  limits = c(0, max(d$value / 86400, na.rm = TRUE))) +
scale_x_date("", breaks = "3 months", minor_breaks = "1 month",
  labels = date_format("%b %Y")) +
scale_colour_manual(name = paste("Requirements for\nconsidering",
  "a\nbridge as stable\n"), breaks = c("perc10tosa50wfu50wmtbac",
  "perc10tosa0wfu50wmtbac", "perc10tosa50wfu0wmtbac",
  "perc10tosa0wfu0wmtbac"), labels = c("WFU & WMTBAC", "WMTBAC", "WFU",
  "None"), values = c("black", "grey80", "grey60", "grey45")) +
opts(plot.title = theme_text(size = 14 * 0.8, face = "bold"),
  axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "tosa-sim.pdf", width = 7, height = 3, dpi = 100)

d <- stability[stability$time > '2010-07' & stability$time < '2011-07', ]
d <- d[, c("time", "stablebridge0wfu50wmtbac", "stablebridge50wfu0wmtbac",
  "stablebridge50wfu50wmtbac", "running")]
d <- na.omit(d)
#d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
#  quantile, probs = 0.5)
d <- rbind(
  data.frame(time = d$time, y = d$stablebridge0wfu50wmtbac / d$running,
    variable = "WMTBAC"),
  data.frame(time = d$time, y = d$stablebridge50wfu0wmtbac / d$running,
    variable = "WFU"),
  data.frame(time = d$time, y = d$stablebridge50wfu50wmtbac / d$running,
    variable = "WFU & WMTBAC"))
d <- aggregate(list(y = d$y), by = list(x = as.Date(d$time),
  variable = d$variable), quantile, probs = 0.5)
missing_dates <- as.Date(setdiff(seq(from = min(d$x, na.rm = TRUE),
  to = max(d$x, na.rm = TRUE), by="1 day"), d$x), origin = "1970-01-01")
d <- rbind(d,
  data.frame(x = missing_dates, y = NA,
    variable = "WMTBAC"),
  data.frame(x = missing_dates, y = NA,
    variable = "WFU"),
  data.frame(x = missing_dates, y = NA,
    variable = "WFU & WMTBAC"))
ggplot(d, aes(x = x, y = y, colour = variable)) +
geom_line() +
scale_y_continuous(name = "Fraction of    \nRunning    \nbridges    ",
  labels = percent, limits = c(0, max(d$y, na.rm = TRUE))) +
scale_x_date("", breaks = "3 months", minor_breaks = "1 month",
  labels = date_format("%b %Y")) +
scale_colour_manual(name = paste("\nRequirements for\nconsidering",
  "a\nbridge as stable\n"), values = c("grey80", "grey60", "grey45")) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "stablebridge.pdf", width = 7, height = 3, dpi = 100)

