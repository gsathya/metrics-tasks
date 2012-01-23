library(ggplot2)

u <- read.csv("download-stats-usa.csv", header = FALSE,
  col.names = c("dirauth", "download", "seconds"))
e <- read.csv("download-stats-europe.csv", header = FALSE,
  col.names = c("dirauth", "download", "seconds"))
d <- rbind(
  data.frame(u, host = "U.S.A."),
  data.frame(e, host = "Europe"))
d <- d[d$download >= min(e$download), ]
ggplot(d, aes(x = as.POSIXct(download / 1000,
  origin = "1970-01-01 00:00:00"), y = seconds / 1000, colour = host)) +
geom_point(alpha = 0.25) +
facet_wrap(~ dirauth) +
scale_x_datetime(name = "", major = "1 week", minor = "1 day",
  format = "%b %d") +
scale_y_continuous(name = "", limits = c(0, max(d$seconds) / 1000)) +
scale_colour_hue(name = "") +
opts(title = "Consensus download times in seconds\n")
ggsave("download-stats-comparison.png", width = 8, height = 6, dpi = 100)

