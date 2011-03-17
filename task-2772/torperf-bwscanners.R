options(warn = -1)
suppressPackageStartupMessages(library("ggplot2"))

a <- read.csv("torperf-stats.csv", stringsAsFactors = FALSE,
  header = TRUE)
a <- a[a$source == "all-1mb",]
ymax <- max(a$md, na.rm = TRUE) / 1e3

b <- read.csv("bwscanner-failures.csv", stringsAsFactors = FALSE,
  header = TRUE)
b <- b[b$end >= min(a$date, na.rm = TRUE),]
b[1,1] <- max(b[1,1], min(a$date, na.rm = TRUE))

ggplot(a, aes(x = as.Date(date), y = md/1e3)) +

geom_line(size = 0.5) +

scale_y_continuous(name = paste("Download time of 1 MB file over Tor",
  "in seconds\n")) +

scale_x_date(name = "") +

geom_rect(aes(NULL, NULL, xmin = as.Date(start), xmax = as.Date(end),
  ymin = -Inf, ymax = Inf, fill = TRUE), data = b) +

scale_fill_manual(name = "", breaks = TRUE,
  labels = "Bandwidth scanners failing", values = alpha("red2", 0.4)) +

opts(legend.position = "bottom")

ggsave(filename = "torperf-bwscanners.pdf", width = 8, height = 6)

