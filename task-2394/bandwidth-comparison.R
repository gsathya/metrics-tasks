options(warn = -1)
suppressPackageStartupMessages(library("ggplot2"))

b <- read.csv("bandwidth-comparison.csv", stringsAsFactors = FALSE)

# Plot ECDF to compare categories
cdf_relays_category <- function(data, category) {
  d <- data[data$category == category & data$descriptorbandwidth > 0, ]
  d <- sort(d$consensusbandwidth * 1000 / d$descriptorbandwidth)
  d <- data.frame(x = d, y = (1:length(d)) / length(d),
    category = category)
  d
}
relays_category <- rbind(
  cdf_relays_category(b, "Guard & Exit (default policy)"),
  cdf_relays_category(b, "Exit (default policy)"),
  cdf_relays_category(b, "Guard & Exit (non-default policy)"),
  cdf_relays_category(b, "Exit (non-default policy)"),
  cdf_relays_category(b, "Guard"),
  cdf_relays_category(b, "Middle"))
ggplot(relays_category, aes(x = x, y = y, colour = category)) +
geom_line() +
scale_x_log10("\nRatio of measured by self-reported bandwidth",
  limits = c(0.1, 10), breaks = c(0.1, 0.2, 0.5, 1, 2, 5, 10),
  labels = c("0.1", "0.2", "0.5", "1", "2", "5", "10")) +
scale_y_continuous("Fraction of relays\n", limits = c(0, 1),
  formatter = "percent") +
scale_colour_hue("") +
geom_vline(xintercept = 1, legend = FALSE, linetype = "dashed") +
opts(title = "Ratio between measured and self-reported relay bandwidth",
  legend.position = "top")
ggsave(filename = "bandwidth-comparison-relays.png",
  width = 8, height = 5, dpi = 150)

# Plot ECDFs to compare consensus to votes
cdf_relays_category_votes <- function(data, category) {
  d <- data[data$category == category & data$descriptorbandwidth > 0, ]
  consensus <- sort(d$consensusbandwidth * 1000 / d$descriptorbandwidth)
  ides <- sort(d$idesbandwidth * 1000 / d$descriptorbandwidth)
  urras <- sort(d$urrasbandwidth * 1000 / d$descriptorbandwidth)
  moria1 <- sort(d$moria1bandwidth * 1000 / d$descriptorbandwidth)
  gabelmoo <- sort(d$gabelmoobandwidth * 1000 / d$descriptorbandwidth)
  maatuska <- sort(d$maatuskabandwidth * 1000 / d$descriptorbandwidth)
  d <- data.frame(x = consensus,
               y = (1:length(consensus)) / length(consensus),
               source = "consensus",
               category = category)
  if (length(urras) > 0) {
    d <- rbind(d, data.frame(x = urras,
               y = (1:length(urras)) / length(urras),
               source = "urras",
               category = category))
  }
  if (length(ides) > 0) {
    d <- rbind(d, data.frame(x = ides,
               y = (1:length(ides)) / length(ides),
               source = "ides",
               category = category))
  }
  if (length(moria1) > 0) {
    d <- rbind(d, data.frame(x = moria1,
               y = (1:length(moria1)) / length(moria1),
               source = "moria1",
               category = category))
  }
  if (length(gabelmoo) > 0) {
    d <- rbind(d, data.frame(x = gabelmoo,
               y = (1:length(gabelmoo)) / length(gabelmoo),
               source = "gabelmoo",
               category = category))
  }
  if (length(maatuska) > 0) {
    d <- rbind(d, data.frame(x = maatuska,
               y = (1:length(maatuska)) / length(maatuska),
               source = "maatuska",
               category = category))
  }
  d
}
relays_category_votes <- rbind(
  cdf_relays_category_votes(b, "Guard & Exit (default policy)"),
  cdf_relays_category_votes(b, "Exit (default policy)"),
  cdf_relays_category_votes(b, "Guard & Exit (non-default policy)"),
  cdf_relays_category_votes(b, "Exit (non-default policy)"),
  cdf_relays_category_votes(b, "Guard"),
  cdf_relays_category_votes(b, "Middle"))
ggplot(relays_category_votes, aes(x = x, y = y, colour = source)) +
geom_line() +
facet_wrap(~ category, ncol = 3) +
scale_x_log10("\nRatio of measured by self-reported bandwidth",
  limits = c(0.1, 10), breaks = c(0.1, 1, 10),
  labels = c("0.1", "1", "10")) +
scale_y_continuous("Fraction of relays\n", limits = c(0, 1),
  formatter = "percent") +
scale_colour_manual("", c("consensus" = "black",
  "urras" = alpha("purple", 0.5), "ides" = alpha("red", 0.5),
  "moria1" = alpha("green", 0.5), "gabelmoo" = alpha("blue", 0.5),
  "maatuska" = alpha("orange", 0.5))) +
geom_vline(xintercept = 1, legend = FALSE, linetype = "dotted") +
opts(title = paste("Measured vs. self-reported bandwidth ratios in",
  "consensus and votes\n"), legend.position = "right")
ggsave(filename = "bandwidth-comparison-relays-votes.png",
  width = 8, height = 5, dpi = 150)

# Plot _weighted_ ECDFs
wecdf <- function(data, source, category) {
  data <- data[with(data, order(ratio)), ]
  sum_measured <- sum(data$measured, na.rm = TRUE)
  cur_measured <- data$measured[1]
  res <- data.frame(x = data$ratio[1],
                  y = cur_measured / sum_measured,
               source = source, category = category)
  for (i in 2:length(data$ratio)) {
    cur_measured <- cur_measured + data$measured[i]
    res <- rbind(res, data.frame(x = data$ratio[i],
        y = cur_measured / sum_measured,
               source = source, category = category))
  }
  res
}
cdf_measured_category_votes <- function(data, category) {
  d <- data[data$category == category & data$descriptorbandwidth > 0, ]
  d <- rbind(
    wecdf(data.frame(
      ratio = d$consensusbandwidth * 1000 / d$descriptorbandwidth,
      measured = d$consensusbandwidth), "consensus", category),
    wecdf(data.frame(
      ratio = d$urrasbandwidth * 1000 / d$descriptorbandwidth,
      measured = d$urrasbandwidth), "urras", category),
    wecdf(data.frame(
      ratio = d$idesbandwidth * 1000 / d$descriptorbandwidth,
      measured = d$idesbandwidth), "ides", category),
    wecdf(data.frame(
      ratio = d$moria1bandwidth * 1000 / d$descriptorbandwidth,
      measured = d$moria1bandwidth), "moria1", category),
    wecdf(data.frame(
      ratio = d$gabelmoobandwidth * 1000 / d$descriptorbandwidth,
      measured = d$gabelmoobandwidth), "gabelmoo", category),
    wecdf(data.frame(
      ratio = d$maatuskabandwidth * 1000 / d$descriptorbandwidth,
      measured = d$maatuskabandwidth), "maatuska", category))
  d
}
measured_category_votes <- rbind(
  cdf_measured_category_votes(b, "Guard & Exit (default policy)"),
  cdf_measured_category_votes(b, "Exit (default policy)"),
  cdf_measured_category_votes(b, "Guard & Exit (non-default policy)"),
  cdf_measured_category_votes(b, "Exit (non-default policy)"),
  cdf_measured_category_votes(b, "Guard"),
  cdf_measured_category_votes(b, "Middle"))
ggplot(measured_category_votes, aes(x = x, y = y, colour = source)) +
geom_line() +
facet_wrap(~ category, ncol = 3) +
scale_x_log10("\nRatio of measured by self-reported bandwidth",
  limits = c(0.1, 10), breaks = c(0.1, 1, 10),
  labels = c("0.1", "1", "10")) +
scale_y_continuous("Fraction of measured bandwidth\n", limits = c(0, 1),
  formatter = "percent") +
scale_colour_manual("", c("consensus" = "black",
  "urras" = alpha("purple", 0.5), "ides" = alpha("red", 0.5),
  "moria1" = alpha("green", 0.5), "gabelmoo" = alpha("blue", 0.5),
  "maatuska" = alpha("orange", 0.5))) +
geom_vline(xintercept = 1, legend = FALSE, linetype = "dotted") +
opts(title = paste("Measured vs. self-reported bandwidth ratios in",
  "consensus and votes\n"), legend.position = "right")
ggsave(filename = "bandwidth-comparison-measured-votes.png",
  width = 8, height = 5, dpi = 150)
write.csv(measured_category_votes, "measured_category_votes-temp.csv",
  quote = FALSE, row.names = FALSE)

