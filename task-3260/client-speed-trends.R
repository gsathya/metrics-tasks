library(ggplot2)
c <- read.csv("out/client-speed-trends.csv", stringsAsFactors = FALSE)
c <- c[as.Date(c$date) < max(as.Date(c$date)) - 1, ]

ggplot(c, aes(x = as.Date(date), y = lines * 2)) +
geom_line() +
scale_y_continuous(name = "Directory mirrors reporting statistics\n",
  limits = c(0, max(c$lines) * 2))

ggplot(c, aes(x = as.Date(date), y = complete)) +
geom_line()

d <- c
d[d$lines < 5, c("lines", "p10", "p20", "p30", "p40", "p50")] <- NA
d <- d[, c("date", "lines", "p10", "p20", "p30", "p40", "p50")]
d <- melt(d, id.vars = c("date", "lines"))
ggplot(d, aes(x = as.Date(date), y = value / 1024, colour = variable,
  alpha = log(lines))) +
geom_line() +
scale_x_date(name = "", limits = as.Date(c(min(d[!is.na(d$value), "date"]),
  max(d[!is.na(d$value), "date"]))), format = "%Y") +
scale_y_continuous(name = "", limits = c(0, 200)) +
  #limits = c(0, max(d$value, na.rm = TRUE) / 1024)) +
scale_colour_hue(name = "Percentile",
  breaks = c("p50", "p40", "p30", "p20", "p10"),
  labels = seq(50, 10, -10), h.start = 60) +
scale_alpha(legend = FALSE) +
opts(title = "Estimated client bandwidth in KiB/s\n")
ggsave("client-speed-trends.png", width = 8, height = 5, dpi = 72)

