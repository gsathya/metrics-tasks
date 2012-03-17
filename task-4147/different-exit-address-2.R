library(ggplot2)
d <- read.csv("different-exit-address-aggregate.csv",
  stringsAsFactors = FALSE)

# Cut off dates before 2012-02-14, because exit lists were stale
# Cut off dates after 2012-02-27, because we only imported February data
# Leaves us with 2 weeks of data; should be fine
d <- d[d$date >= "2012-02-14" & d$date <= "2012-02-27", ]

a <- aggregate(x = list(writtenbytes = d$writtenbytes),
  by = list(date = d$date), FUN = sum)
ggplot(a, aes(x = as.Date(date), y = writtenbytes / 2^20 / 86400)) +
geom_line() +
scale_x_date(name = "") +
scale_y_continuous(name = "MiB/s\n",
  limits = c(0, max(a$writtenbytes) / 2^20 / 86400)) +
opts(title = "Bytes written by all relays with the Exit flag\n")

ggplot(d, aes(x = as.Date(date), y = writtenbytes / 2^20 / 86400,
  colour = differentaddress)) +
geom_line() +
scale_x_date(name = "") +
scale_y_continuous(name = "MiB/s\n",
  limits = c(0, max(d$writtenbytes / 2^20 / 86400))) +
opts(title = "Bytes written by all relays with the Exit flag\n")

s <- cast(d, date ~ differentaddress)
s <- data.frame(date = s$date, fracdifferent = s[, "TRUE"] / s[, "FALSE"])
ggplot(s, aes(x = as.Date(date), y = fracdifferent)) +
geom_line() +
scale_x_date(name = "", format = "%Y-%m-%d") +
scale_y_continuous(name = "", formatter = "percent", limits = c(0, 1)) +
opts(title =
  paste("Fraction of bytes written by relays with the Exit flag\n",
        "which could have used a different address for exiting\n",
        "than the relay used for registering in the Tor network\n",
        sep = ""))
ggsave("different-exit-address.png", width = 8, height = 5, dpi = 72)

