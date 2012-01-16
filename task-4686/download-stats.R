library(ggplot2)
d <- read.csv("download-stats.csv", header = FALSE,
  col.names = c("dirauth", "download", "seconds"))
ggplot(d, aes(x = as.POSIXct(download / 1000,
  origin = "1970-01-01 00:00:00"), y = seconds / 1000)) +
geom_point(alpha = 0.25) +
facet_wrap(~ dirauth) +
scale_x_datetime(name = "", major = "1 month", minor = "1 week",
  format = "%b %d, %Y") +
scale_y_continuous(name = "", limits = c(0, max(d$seconds) / 1000)) +
opts(title = "Consensus download times in seconds\n")
ggsave("download-stats.png", width = 8, height = 6, dpi = 100)

