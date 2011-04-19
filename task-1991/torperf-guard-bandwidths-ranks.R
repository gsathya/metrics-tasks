library(ggplot2)

# Read data
data <- read.csv("torperf-guard-bandwidths-ranks.csv",
  stringsAsFactors = FALSE)

data <- data[(data$filesize == "50kb" & data$completiontime < 60000) |
             (data$filesize == "1mb" & data$completiontime < 120000) |
             (data$filesize == "5mb" & data$completiontime < 300000), ]

data[data$guards == "slow", "guards"] <- "a) slowest overall"
data[data$guards == "slowratio", "guards"] <- "b) slowest ratio"
data[data$guards == "regular", "guards"] <- "c) default"
data[data$guards == "fastratio", "guards"] <- "d) fastest ratio"
data[data$guards == "fast", "guards"] <- "e) fastest overall"
data[data$filesize == "50kb", "filesize"] <- "a) 50 KB"
data[data$filesize == "1mb", "filesize"] <- "b) 1 MB"
data[data$filesize == "5mb", "filesize"] <- "c) 5 MB"

ggplot(data, aes(x = bandwidth / 1000, y = completiontime / 1000)) +
geom_point(alpha = 0.05) +
scale_x_continuous("\nGuard consensus bandwidth in MB/s") +
scale_y_continuous("Torperf completion time in seconds\n") +
facet_grid(filesize ~ guards, scale = "free_y") +
opts(legend.position = "none")
ggsave(filename = "torperf-guard-bandwidths.png",
  width = 8, height = 5, dpi = 150)

ggplot(data, aes(x = as.numeric(rank), y = completiontime / 1000)) +
geom_point(alpha = 0.05) +
scale_x_continuous(paste("\nGuard rank by consensus bandwidth from",
  "slowest (0) to fastest (1)"), limits = c(0, 1),
  breaks = c(0.25, 0.5, 0.75)) +
scale_y_continuous("Torperf completion time in seconds\n") +
facet_grid(filesize ~ guards, scale = "free_y") +
opts(legend.position = "none")
ggsave(filename = "torperf-guard-ranks.png",
  width = 8, height = 5, dpi = 150)


